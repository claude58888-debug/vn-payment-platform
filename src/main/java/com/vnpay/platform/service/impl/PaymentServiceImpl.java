package com.vnpay.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.common.BusinessException;
import com.vnpay.platform.common.SnowflakeIdGenerator;
import com.vnpay.platform.config.RabbitMQConfig;
import com.vnpay.platform.entity.ChannelConfig;
import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.mapper.PaymentTransactionMapper;
import com.vnpay.platform.service.CallbackService;
import com.vnpay.platform.service.ChannelAdapter;
import com.vnpay.platform.service.LedgerService;
import com.vnpay.platform.service.MerchantService;
import com.vnpay.platform.service.PaymentService;
import com.vnpay.platform.service.RiskControlService;
import com.vnpay.platform.service.RoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionMapper transactionMapper;
    private final MerchantService merchantService;
    private final RoutingService routingService;
    private final ChannelAdapter channelAdapter;
    private final RiskControlService riskControlService;
    private final CallbackService callbackService;
    private final LedgerService ledgerService;
    private final SnowflakeIdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> createPayment(Map<String, Object> params) {
        String merchantId = (String) params.get("merchantId");
        String merchantOrderId = (String) params.get("merchantOrderId");
        String amountStr = params.get("amount") != null ? params.get("amount").toString() : null;
        String payMethod = (String) params.get("payMethod");
        String notifyUrl = (String) params.get("notifyUrl");

        // Validate required fields
        if (merchantId == null || merchantOrderId == null || amountStr == null) {
            return ApiResponse.badRequest("merchantId, merchantOrderId, and amount are required");
        }

        BigDecimal amount = new BigDecimal(amountStr);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.badRequest("Amount must be positive");
        }

        // Validate merchant
        Merchant merchant = merchantService.getByMerchantId(merchantId);
        if (merchant == null) {
            return ApiResponse.notFound("Merchant not found: " + merchantId);
        }
        if (!"ACTIVE".equals(merchant.getStatus())) {
            return ApiResponse.error(403, "Merchant is not active");
        }

        // Check limits
        if (amount.longValue() > merchant.getSingleLimitVnd()) {
            return ApiResponse.error(400, "Amount exceeds single transaction limit");
        }

        // Risk control pre-check
        String clientIp = (String) params.get("clientIp");
        if (!riskControlService.preTransactionCheck(merchant, amount, payMethod, clientIp)) {
            return ApiResponse.error(403, "Transaction rejected by risk control");
        }

        // Idempotency check
        String idempotencyKey = "payment:idem:" + merchantId + ":" + merchantOrderId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1",
                Duration.ofMinutes(30));
        if (Boolean.FALSE.equals(isNew)) {
            // Check if existing transaction
            PaymentTransaction existing = transactionMapper.selectOne(
                    new LambdaQueryWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getMerchantId, merchantId)
                            .eq(PaymentTransaction::getMerchantOrderId, merchantOrderId));
            if (existing != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("transactionId", existing.getTransactionId());
                data.put("status", existing.getStatus());
                return ApiResponse.success(data);
            }
        }

        // Route to channel
        ChannelConfig channel = routingService.selectChannel(
                payMethod != null ? payMethod : "bank_transfer", amount);

        // Generate transaction ID
        String transactionId = "T" + idGenerator.nextStringId();

        // Calculate fee
        BigDecimal feeAmount = amount.multiply(merchant.getFeeRate())
                .setScale(2, RoundingMode.HALF_UP);

        // Create transaction record
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTransactionId(transactionId);
        transaction.setMerchantId(merchantId);
        transaction.setMerchantOrderId(merchantOrderId);
        transaction.setAmount(amount);
        transaction.setCurrency("VND");
        transaction.setPayMethod(payMethod != null ? payMethod : "bank_transfer");
        transaction.setChannelId(channel.getChannelId());
        transaction.setStatus("CREATED");
        transaction.setCallbackStatus("PENDING");
        transaction.setCallbackCount(0);
        transaction.setNotifyUrl(notifyUrl != null ? notifyUrl : merchant.getNotifyUrl());
        transaction.setFeeAmount(feeAmount);

        transactionMapper.insert(transaction);

        // Submit to channel
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> channelResult = channelAdapter.submitPayment(
                    channel, transactionId, amount, transaction.getPayMethod(), params);

            String channelTxId = (String) channelResult.get("channelTransactionId");
            transaction.setChannelTransactionId(channelTxId);
            transaction.setStatus("PROCESSING");

            transactionMapper.update(null,
                    new LambdaUpdateWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getTransactionId, transactionId)
                            .set(PaymentTransaction::getChannelTransactionId, channelTxId)
                            .set(PaymentTransaction::getStatus, "PROCESSING"));

            routingService.recordResult(channel.getChannelId(), true,
                    System.currentTimeMillis() - startTime);

            // Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("transactionId", transactionId);
            responseData.put("status", "PROCESSING");
            responseData.put("channelTransactionId", channelTxId);
            responseData.putAll(channelResult);

            // Cache transaction in Redis
            redisTemplate.opsForValue().set("tx:" + transactionId, "PROCESSING",
                    Duration.ofHours(2));

            log.info("Payment created: txId={}, merchantId={}, amount={}, channel={}",
                    transactionId, merchantId, amount, channel.getChannelId());

            return ApiResponse.success(responseData);

        } catch (Exception e) {
            routingService.recordResult(channel.getChannelId(), false,
                    System.currentTimeMillis() - startTime);

            transaction.setStatus("FAILED");
            transactionMapper.update(null,
                    new LambdaUpdateWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getTransactionId, transactionId)
                            .set(PaymentTransaction::getStatus, "FAILED"));

            log.error("Payment submission failed: txId={}, channel={}", transactionId,
                    channel.getChannelId(), e);
            return ApiResponse.error("Payment submission failed: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse<PaymentTransaction> getPaymentStatus(String transactionId, String merchantId) {
        PaymentTransaction transaction = transactionMapper.selectOne(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getTransactionId, transactionId)
                        .eq(PaymentTransaction::getMerchantId, merchantId));

        if (transaction == null) {
            return ApiResponse.notFound("Transaction not found");
        }

        return ApiResponse.success(transaction);
    }

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> refundPayment(String transactionId, String merchantId,
                                                           String reason) {
        PaymentTransaction transaction = transactionMapper.selectOne(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getTransactionId, transactionId)
                        .eq(PaymentTransaction::getMerchantId, merchantId));

        if (transaction == null) {
            return ApiResponse.notFound("Transaction not found");
        }

        if (!"SUCCESS_CONFIRMED".equals(transaction.getStatus())
                && !"SUCCESS_PENDING_RECON".equals(transaction.getStatus())) {
            return ApiResponse.badRequest("Only successful transactions can be refunded");
        }

        // Record refund ledger
        ledgerService.recordRefund(merchantId, transactionId, transaction.getAmount());

        Map<String, Object> result = new HashMap<>();
        result.put("transactionId", transactionId);
        result.put("refundStatus", "PROCESSING");
        result.put("refundAmount", transaction.getAmount());

        log.info("Refund initiated: txId={}, merchantId={}, amount={}",
                transactionId, merchantId, transaction.getAmount());

        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public void updateTransactionStatus(String transactionId, String status, String matchSource) {
        PaymentTransaction transaction = getByTransactionId(transactionId);
        if (transaction == null) {
            throw new BusinessException(404, "Transaction not found: " + transactionId);
        }

        String oldStatus = transaction.getStatus();

        transactionMapper.update(null,
                new LambdaUpdateWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getTransactionId, transactionId)
                        .set(PaymentTransaction::getStatus, status)
                        .set(matchSource != null, PaymentTransaction::getMatchSource, matchSource));

        // Update Redis cache
        redisTemplate.opsForValue().set("tx:" + transactionId, status, Duration.ofHours(2));

        // Record ledger on success
        if ("SUCCESS_CONFIRMED".equals(status) || "SUCCESS_PENDING_RECON".equals(status)) {
            if (!"SUCCESS_CONFIRMED".equals(oldStatus) && !"SUCCESS_PENDING_RECON".equals(oldStatus)) {
                ledgerService.recordPayment(transaction.getMerchantId(), transactionId,
                        transaction.getAmount(), transaction.getFeeAmount());
            }
        }

        // Send callback to merchant
        transaction.setStatus(status);
        transaction.setMatchSource(matchSource);
        callbackService.sendCallback(transaction);

        // Post-transaction risk analysis
        riskControlService.postTransactionAnalysis(transaction);

        log.info("Transaction status updated: txId={}, {} -> {}, matchSource={}",
                transactionId, oldStatus, status, matchSource);
    }

    @Override
    public PaymentTransaction getByTransactionId(String transactionId) {
        return transactionMapper.selectOne(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getTransactionId, transactionId));
    }
}
