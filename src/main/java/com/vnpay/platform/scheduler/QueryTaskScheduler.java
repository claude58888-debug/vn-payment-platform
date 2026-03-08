package com.vnpay.platform.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vnpay.platform.entity.ChannelConfig;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.mapper.ChannelConfigMapper;
import com.vnpay.platform.mapper.PaymentTransactionMapper;
import com.vnpay.platform.service.ChannelAdapter;
import com.vnpay.platform.service.PaymentService;
import com.vnpay.platform.service.RoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Scheduled task to poll upstream channels for PROCESSING order status.
 * This implements Path A (Primary) confirmation:
 * Upstream bank/channel query API -> platform internal final status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTaskScheduler {

    private final PaymentTransactionMapper transactionMapper;
    private final ChannelConfigMapper channelConfigMapper;
    private final ChannelAdapter channelAdapter;
    private final PaymentService paymentService;
    private final RoutingService routingService;

    /**
     * Poll upstream channels every 30 seconds for PROCESSING transactions.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void pollProcessingTransactions() {
        log.debug("Starting query task: polling PROCESSING transactions");

        // Get transactions created within the last 24 hours that are still PROCESSING
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<PaymentTransaction> processingTxs = transactionMapper.findProcessingTransactions(
                since, 100);

        if (processingTxs.isEmpty()) {
            log.debug("No PROCESSING transactions to poll");
            return;
        }

        log.info("Polling {} PROCESSING transactions", processingTxs.size());

        for (PaymentTransaction tx : processingTxs) {
            try {
                queryAndUpdateTransaction(tx);
            } catch (Exception e) {
                log.error("Failed to query transaction: txId={}, channelId={}",
                        tx.getTransactionId(), tx.getChannelId(), e);
            }
        }
    }

    private void queryAndUpdateTransaction(PaymentTransaction tx) {
        // Get channel config
        ChannelConfig channel = channelConfigMapper.selectOne(
                new LambdaQueryWrapper<ChannelConfig>()
                        .eq(ChannelConfig::getChannelId, tx.getChannelId()));

        if (channel == null || !Boolean.TRUE.equals(channel.getHasQueryApi())) {
            log.debug("Channel {} has no query API, skipping", tx.getChannelId());
            return;
        }

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = channelAdapter.queryTransaction(
                channel, tx.getTransactionId(), tx.getChannelTransactionId());

        long latency = System.currentTimeMillis() - startTime;
        String channelStatus = (String) result.get("status");

        if ("SUCCESS".equals(channelStatus)) {
            // Channel confirmed success -> update to SUCCESS_PENDING_RECON
            paymentService.updateTransactionStatus(
                    tx.getTransactionId(), "SUCCESS_PENDING_RECON", "CHANNEL");
            routingService.recordResult(tx.getChannelId(), true, latency);

            log.info("Channel query success: txId={}, channelId={}",
                    tx.getTransactionId(), tx.getChannelId());

        } else if ("FAILED".equals(channelStatus)) {
            paymentService.updateTransactionStatus(
                    tx.getTransactionId(), "FAILED", "CHANNEL");
            routingService.recordResult(tx.getChannelId(), false, latency);

            log.info("Channel query failed: txId={}, channelId={}",
                    tx.getTransactionId(), tx.getChannelId());

        } else {
            // Still processing, check if it's been too long
            if (tx.getCreatedAt().isBefore(LocalDateTime.now().minusHours(2))) {
                log.warn("Transaction stuck in PROCESSING for >2h: txId={}", tx.getTransactionId());
            }
        }
    }

    /**
     * Check for half-open circuit breakers every 60 seconds.
     * If a channel was circuit-broken, try a test query after cooldown.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 120000)
    public void checkCircuitBreakers() {
        List<ChannelConfig> openChannels = channelConfigMapper.selectList(
                new LambdaQueryWrapper<ChannelConfig>()
                        .eq(ChannelConfig::getCircuitBreakerStatus, "OPEN"));

        for (ChannelConfig channel : openChannels) {
            // Check if cooldown has passed (managed via Redis TTL in RoutingService)
            // If half-open period reached, set to HALF_OPEN for test traffic
            channelConfigMapper.updateCircuitBreakerStatus(
                    channel.getChannelId(), "HALF_OPEN");
            log.info("Circuit breaker HALF_OPEN for channel: {}", channel.getChannelId());
        }
    }
}
