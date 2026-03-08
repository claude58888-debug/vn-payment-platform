package com.vnpay.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnpay.platform.common.HmacUtil;
import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.mapper.PaymentTransactionMapper;
import com.vnpay.platform.service.CallbackService;
import com.vnpay.platform.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackServiceImpl implements CallbackService {

    private final PaymentTransactionMapper transactionMapper;
    private final MerchantService merchantService;
    private final ObjectMapper objectMapper;

    @Value("${platform.callback.max-retry:6}")
    private int maxRetry;

    @Value("${platform.callback.retry-intervals:0,30,120,600,3600}")
    private String retryIntervalsStr;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @Async
    public void sendCallback(PaymentTransaction transaction) {
        String notifyUrl = transaction.getNotifyUrl();
        if (notifyUrl == null || notifyUrl.isEmpty()) {
            log.debug("No notify URL for transaction: {}", transaction.getTransactionId());
            return;
        }

        // Only callback on meaningful status changes
        String status = transaction.getStatus();
        if (!"SUCCESS_PENDING_RECON".equals(status) && !"SUCCESS_CONFIRMED".equals(status)
                && !"FAILED".equals(status)) {
            return;
        }

        try {
            Merchant merchant = merchantService.getByMerchantId(transaction.getMerchantId());
            if (merchant == null) {
                log.warn("Merchant not found for callback: {}", transaction.getMerchantId());
                return;
            }

            // Build callback payload
            Map<String, String> payload = new HashMap<>();
            payload.put("transactionId", transaction.getTransactionId());
            payload.put("merchantOrderId", transaction.getMerchantOrderId());
            payload.put("status", status);
            payload.put("amount", transaction.getAmount().toPlainString());
            payload.put("currency", transaction.getCurrency());
            payload.put("timestamp", String.valueOf(System.currentTimeMillis()));

            // Sign payload
            String signString = HmacUtil.buildSignString(payload);
            String signature = HmacUtil.sign(signString, merchant.getMerchantSecret());
            payload.put("signature", signature);

            // Send HTTP POST
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

            restTemplate.postForEntity(notifyUrl, request, String.class);

            // Update callback status
            transactionMapper.update(null,
                    new LambdaUpdateWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getTransactionId, transaction.getTransactionId())
                            .set(PaymentTransaction::getCallbackStatus, "SENT")
                            .set(PaymentTransaction::getCallbackCount,
                                    transaction.getCallbackCount() + 1));

            log.info("Callback sent: txId={}, url={}, status={}",
                    transaction.getTransactionId(), notifyUrl, status);

        } catch (Exception e) {
            log.error("Callback failed: txId={}, url={}", transaction.getTransactionId(),
                    notifyUrl, e);

            int count = transaction.getCallbackCount() + 1;
            String cbStatus = count >= maxRetry ? "FAILED" : "PENDING";

            transactionMapper.update(null,
                    new LambdaUpdateWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getTransactionId, transaction.getTransactionId())
                            .set(PaymentTransaction::getCallbackStatus, cbStatus)
                            .set(PaymentTransaction::getCallbackCount, count));

            if (count < maxRetry) {
                log.info("Will retry callback: txId={}, attempt={}/{}",
                        transaction.getTransactionId(), count, maxRetry);
            }
        }
    }

    @Override
    public void retryCallback(String transactionId) {
        PaymentTransaction transaction = transactionMapper.selectOne(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getTransactionId, transactionId));

        if (transaction == null) {
            log.warn("Transaction not found for callback retry: {}", transactionId);
            return;
        }

        if (transaction.getCallbackCount() >= maxRetry) {
            log.warn("Max callback retries reached: txId={}, count={}",
                    transactionId, transaction.getCallbackCount());
            return;
        }

        sendCallback(transaction);
    }

    @Override
    public void confirmCallback(String transactionId, String merchantId) {
        transactionMapper.update(null,
                new LambdaUpdateWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getTransactionId, transactionId)
                        .eq(PaymentTransaction::getMerchantId, merchantId)
                        .set(PaymentTransaction::getCallbackStatus, "CONFIRMED"));

        log.info("Callback confirmed: txId={}, merchantId={}", transactionId, merchantId);
    }
}
