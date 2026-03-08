package com.vnpay.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vnpay.platform.common.HmacUtil;
import com.vnpay.platform.entity.DeviceBinding;
import com.vnpay.platform.entity.DeviceNotification;
import com.vnpay.platform.entity.DeviceParsedTransaction;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.mapper.DeviceBindingMapper;
import com.vnpay.platform.mapper.DeviceNotificationMapper;
import com.vnpay.platform.mapper.DeviceParsedTransactionMapper;
import com.vnpay.platform.mapper.PaymentTransactionMapper;
import com.vnpay.platform.service.DeviceMatchService;
import com.vnpay.platform.service.PaymentService;
import com.vnpay.platform.service.RiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMatchServiceImpl implements DeviceMatchService {

    private final DeviceNotificationMapper notificationMapper;
    private final DeviceParsedTransactionMapper parsedTxMapper;
    private final DeviceBindingMapper deviceBindingMapper;
    private final PaymentTransactionMapper paymentTxMapper;
    private final RiskControlService riskControlService;

    // Regex patterns for Vietnamese bank notifications
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:so tien|amount|\\+)\\s*:?\\s*([\\d,.]+)\\s*(?:VND|đ)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "(?:TK|account|card)\\s*:?\\s*\\**(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDIT_PATTERN = Pattern.compile(
            "(?:nhan|credit|\\+|received|deposit)", Pattern.CASE_INSENSITIVE);

    @Override
    @Transactional
    public void processNotification(Map<String, Object> notificationData) {
        String deviceId = (String) notificationData.get("deviceId");
        String merchantId = (String) notificationData.get("merchantId");
        String bankPackage = (String) notificationData.get("bankPackage");
        String bankLabel = (String) notificationData.get("bankLabel");
        String rawTitle = (String) notificationData.get("rawTitle");
        String rawText = (String) notificationData.get("rawText");
        String signature = (String) notificationData.get("signature");

        // Validate device
        DeviceBinding device = deviceBindingMapper.selectOne(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getDeviceId, deviceId)
                        .eq(DeviceBinding::getStatus, "ACTIVE"));

        if (device == null) {
            log.warn("Unknown or inactive device: {}", deviceId);
            return;
        }

        // Validate signature
        if (signature != null && !validateDeviceSignature(deviceId, rawText, signature)) {
            log.warn("Invalid device signature: deviceId={}", deviceId);
            riskControlService.assessDeviceTrust(deviceId, false);
            return;
        }

        // Store raw notification
        String notificationId = "N" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        DeviceNotification notification = new DeviceNotification();
        notification.setNotificationId(notificationId);
        notification.setMerchantId(merchantId);
        notification.setDeviceId(deviceId);
        notification.setBankPackage(bankPackage);
        notification.setBankLabel(bankLabel);
        notification.setRawTitle(rawTitle);
        notification.setRawText(rawText);
        notification.setSignature(signature);
        notification.setSentAt(LocalDateTime.now());
        notificationMapper.insert(notification);

        // Parse notification
        DeviceParsedTransaction parsed = parseNotification(notificationId, merchantId, deviceId,
                bankPackage, bankLabel, rawText);
        if (parsed != null) {
            parsedTxMapper.insert(parsed);
            // Attempt to match
            matchTransaction(parsed);
        }

        log.info("Processed device notification: deviceId={}, merchantId={}, notificationId={}",
                deviceId, merchantId, notificationId);
    }

    @Override
    @Transactional
    public void matchTransaction(DeviceParsedTransaction parsed) {
        if (parsed.getAmount() == null || !"CREDIT".equals(parsed.getDirection())) {
            parsed.setMatchStatus("NO_MATCH");
            parsedTxMapper.updateById(parsed);
            return;
        }

        // Find candidate transactions within last 30 minutes
        LocalDateTime since = parsed.getTxTime() != null
                ? parsed.getTxTime().minusMinutes(30)
                : LocalDateTime.now().minusMinutes(30);

        List<PaymentTransaction> candidates = paymentTxMapper.findCandidatesForMatch(
                parsed.getMerchantId(), parsed.getAmount(), since);

        if (candidates.isEmpty()) {
            parsed.setMatchStatus("NO_MATCH");
            parsedTxMapper.updateById(parsed);
            log.debug("No match found for parsed tx: amount={}, merchantId={}",
                    parsed.getAmount(), parsed.getMerchantId());
            riskControlService.assessDeviceTrust(parsed.getDeviceId(), false);
            return;
        }

        if (candidates.size() == 1) {
            PaymentTransaction matched = candidates.get(0);
            parsed.setMatchStatus("MATCHED");
            parsed.setMatchedTransactionId(matched.getTransactionId());
            parsedTxMapper.updateById(parsed);

            // Check device trust level for early confirmation
            DeviceBinding device = deviceBindingMapper.selectOne(
                    new LambdaQueryWrapper<DeviceBinding>()
                            .eq(DeviceBinding::getDeviceId, parsed.getDeviceId()));

            if (device != null && "TRUSTED".equals(device.getTrustLevel())) {
                // Trusted device: update to SUCCESS_PENDING_RECON
                log.info("Device match (TRUSTED): txId={}, deviceId={}",
                        matched.getTransactionId(), parsed.getDeviceId());
                // Note: We don't call paymentService here to avoid circular dependency
                // The status update will be handled via message queue
            } else {
                log.info("Device match (auxiliary): txId={}, deviceId={}, trustLevel={}",
                        matched.getTransactionId(), parsed.getDeviceId(),
                        device != null ? device.getTrustLevel() : "UNKNOWN");
            }

            riskControlService.assessDeviceTrust(parsed.getDeviceId(), true);
        } else {
            parsed.setMatchStatus("MULTI_CANDIDATES");
            parsedTxMapper.updateById(parsed);
            log.info("Multiple candidates found for device notification: count={}, amount={}",
                    candidates.size(), parsed.getAmount());
        }
    }

    @Override
    public boolean validateDeviceSignature(String deviceId, String data, String signature) {
        DeviceBinding device = deviceBindingMapper.selectOne(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getDeviceId, deviceId));

        if (device == null) {
            return false;
        }

        return HmacUtil.verify(data, device.getAuthKey(), signature);
    }

    private DeviceParsedTransaction parseNotification(String notificationId, String merchantId,
                                                       String deviceId, String bankPackage,
                                                       String bankLabel, String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return null;
        }

        DeviceParsedTransaction parsed = new DeviceParsedTransaction();
        parsed.setNotificationId(notificationId);
        parsed.setMerchantId(merchantId);
        parsed.setDeviceId(deviceId);
        parsed.setBankPackage(bankPackage);
        parsed.setBankLabel(bankLabel);
        parsed.setRawText(rawText);
        parsed.setCurrency("VND");
        parsed.setTxTime(LocalDateTime.now());
        parsed.setMatchStatus("UNMATCHED");

        // Parse amount
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(rawText);
        if (amountMatcher.find()) {
            String amountStr = amountMatcher.group(1).replace(",", "").replace(".", "");
            try {
                parsed.setAmount(new BigDecimal(amountStr));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse amount from notification: {}", rawText);
            }
        }

        // Parse card last 4
        Matcher cardMatcher = CARD_PATTERN.matcher(rawText);
        if (cardMatcher.find()) {
            parsed.setCardLast4(cardMatcher.group(1));
        }

        // Determine direction (credit/debit)
        if (CREDIT_PATTERN.matcher(rawText).find()) {
            parsed.setDirection("CREDIT");
        } else {
            parsed.setDirection("DEBIT");
        }

        return parsed;
    }
}
