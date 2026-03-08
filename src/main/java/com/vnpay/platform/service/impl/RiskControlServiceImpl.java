package com.vnpay.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vnpay.platform.entity.DeviceBinding;
import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.MerchantUsdtAccount;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.mapper.DeviceBindingMapper;
import com.vnpay.platform.mapper.MerchantMapper;
import com.vnpay.platform.mapper.MerchantUsdtAccountMapper;
import com.vnpay.platform.service.RiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlServiceImpl implements RiskControlService {

    private final MerchantMapper merchantMapper;
    private final MerchantUsdtAccountMapper usdtAccountMapper;
    private final DeviceBindingMapper deviceBindingMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String DAILY_AMOUNT_KEY = "risk:daily:amount:";
    private static final String DAILY_FAIL_KEY = "risk:daily:fail:";
    private static final String IP_REQUEST_KEY = "risk:ip:";
    private static final BigDecimal MIN_RISK_EQUITY = new BigDecimal("1000000"); // 1M VND

    @Override
    public boolean preTransactionCheck(Merchant merchant, BigDecimal amount, String payMethod,
                                        String clientIp) {
        // 1. Check merchant status
        if (!"ACTIVE".equals(merchant.getStatus())) {
            log.warn("Risk: merchant not active: {}", merchant.getMerchantId());
            return false;
        }

        // 2. Check single transaction limit
        if (amount.longValue() > merchant.getSingleLimitVnd()) {
            log.warn("Risk: amount {} exceeds single limit {} for merchant {}",
                    amount, merchant.getSingleLimitVnd(), merchant.getMerchantId());
            return false;
        }

        // 3. Check daily limit
        String dailyKey = DAILY_AMOUNT_KEY + merchant.getMerchantId();
        String dailyAmountStr = redisTemplate.opsForValue().get(dailyKey);
        long dailyAmount = dailyAmountStr != null ? Long.parseLong(dailyAmountStr) : 0;
        if (dailyAmount + amount.longValue() > merchant.getDailyLimitVnd()) {
            log.warn("Risk: daily limit exceeded for merchant {}. Current: {}, Requested: {}, Limit: {}",
                    merchant.getMerchantId(), dailyAmount, amount, merchant.getDailyLimitVnd());
            return false;
        }

        // 4. Check daily failure count
        String failKey = DAILY_FAIL_KEY + merchant.getMerchantId();
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
        if (failCount >= 10) {
            log.warn("Risk: too many daily failures for merchant {}: {}", 
                    merchant.getMerchantId(), failCount);
            return false;
        }

        // 5. Check risk equity
        MerchantUsdtAccount usdtAccount = usdtAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantUsdtAccount>()
                        .eq(MerchantUsdtAccount::getMerchantId, merchant.getMerchantId()));
        if (usdtAccount != null && usdtAccount.getRiskEquityVnd().compareTo(MIN_RISK_EQUITY) < 0) {
            log.warn("Risk: low risk equity for merchant {}: {} VND",
                    merchant.getMerchantId(), usdtAccount.getRiskEquityVnd());
            return false;
        }

        // 6. IP rate limiting
        if (clientIp != null) {
            String ipKey = IP_REQUEST_KEY + clientIp;
            Long ipCount = redisTemplate.opsForValue().increment(ipKey);
            if (ipCount != null && ipCount == 1) {
                redisTemplate.expire(ipKey, Duration.ofMinutes(1));
            }
            if (ipCount != null && ipCount > 30) {
                log.warn("Risk: IP rate limit exceeded: {}", clientIp);
                return false;
            }
        }

        // Update daily amount tracking
        redisTemplate.opsForValue().increment(dailyKey, amount.longValue());
        if (dailyAmountStr == null) {
            redisTemplate.expire(dailyKey, Duration.ofHours(24));
        }

        return true;
    }

    @Override
    public void postTransactionAnalysis(PaymentTransaction transaction) {
        if ("FAILED".equals(transaction.getStatus())) {
            // Increment daily failure counter
            String failKey = DAILY_FAIL_KEY + transaction.getMerchantId();
            Long failCount = redisTemplate.opsForValue().increment(failKey);
            if (failCount != null && failCount == 1) {
                redisTemplate.expire(failKey, Duration.ofHours(24));
            }
        }

        log.debug("Post-transaction risk analysis: txId={}, status={}",
                transaction.getTransactionId(), transaction.getStatus());
    }

    @Override
    public void checkRiskEquity(String merchantId) {
        MerchantUsdtAccount account = usdtAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantUsdtAccount>()
                        .eq(MerchantUsdtAccount::getMerchantId, merchantId));

        if (account == null) {
            return;
        }

        // Recalculate risk equity: (usdt_balance - usdt_frozen) * mark_price
        BigDecimal available = account.getUsdtBalance().subtract(account.getUsdtFrozen());
        BigDecimal riskEquity = available.multiply(account.getMarkPriceUsdtVnd())
                .setScale(2, RoundingMode.HALF_UP);

        account.setRiskEquityVnd(riskEquity);
        usdtAccountMapper.updateById(account);

        // Check thresholds and adjust merchant limits
        if (riskEquity.compareTo(MIN_RISK_EQUITY) < 0) {
            log.warn("Low risk equity for merchant {}: {} VND. Consider reducing limits.",
                    merchantId, riskEquity);

            // Auto-reduce daily limit to 50% when equity is low
            Merchant merchant = merchantMapper.selectOne(
                    new LambdaQueryWrapper<Merchant>()
                            .eq(Merchant::getMerchantId, merchantId));
            if (merchant != null && "NORMAL".equals(merchant.getRiskLevel())) {
                merchantMapper.update(null,
                        new LambdaUpdateWrapper<Merchant>()
                                .eq(Merchant::getMerchantId, merchantId)
                                .set(Merchant::getRiskLevel, "HIGH")
                                .set(Merchant::getDailyLimitVnd,
                                        merchant.getDailyLimitVnd() / 2));
                log.warn("Merchant {} risk level elevated to HIGH, daily limit halved",
                        merchantId);
            }
        }
    }

    @Override
    public void assessDeviceTrust(String deviceId, boolean matched) {
        DeviceBinding device = deviceBindingMapper.selectOne(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getDeviceId, deviceId));

        if (device == null) {
            return;
        }

        // Update match accuracy
        BigDecimal currentAccuracy = device.getMatchAccuracy();
        BigDecimal newAccuracy;

        if (matched) {
            // Increase accuracy: weighted average toward 1.0
            newAccuracy = currentAccuracy.multiply(new BigDecimal("0.9"))
                    .add(new BigDecimal("0.1"))
                    .setScale(4, RoundingMode.HALF_UP);
        } else {
            // Decrease accuracy: weighted average toward 0.0
            newAccuracy = currentAccuracy.multiply(new BigDecimal("0.9"))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        String trustLevel = device.getTrustLevel();
        // Promote to TRUSTED if accuracy > 0.8 and currently NEW
        if (newAccuracy.compareTo(new BigDecimal("0.8")) >= 0 && "NEW".equals(trustLevel)) {
            trustLevel = "TRUSTED";
            log.info("Device promoted to TRUSTED: deviceId={}, accuracy={}",
                    deviceId, newAccuracy);
        }
        // Demote to SUSPICIOUS if accuracy drops below 0.3
        else if (newAccuracy.compareTo(new BigDecimal("0.3")) < 0
                && !"BLOCKED".equals(trustLevel)) {
            trustLevel = "SUSPICIOUS";
            log.warn("Device demoted to SUSPICIOUS: deviceId={}, accuracy={}",
                    deviceId, newAccuracy);
        }

        deviceBindingMapper.update(null,
                new LambdaUpdateWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getDeviceId, deviceId)
                        .set(DeviceBinding::getMatchAccuracy, newAccuracy)
                        .set(DeviceBinding::getTrustLevel, trustLevel));
    }

    @Override
    public boolean amlCheck(String depositAddress, String network) {
        // Simplified AML check - in production, would check against sanctioned address lists,
        // mixer services, and risk databases
        if (depositAddress == null || depositAddress.isEmpty()) {
            return false;
        }

        // Check Redis blacklist
        String blacklistKey = "aml:blacklist:" + depositAddress;
        Boolean isBlacklisted = redisTemplate.hasKey(blacklistKey);

        if (Boolean.TRUE.equals(isBlacklisted)) {
            log.warn("AML: Address is blacklisted: {}", depositAddress);
            return false;
        }

        log.debug("AML check passed for address: {}", depositAddress);
        return true;
    }
}
