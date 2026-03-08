package com.vnpay.platform.service;

import com.vnpay.platform.entity.ChannelConfig;

import java.math.BigDecimal;

public interface RoutingService {

    /**
     * Select the best channel based on pay method, amount, and routing weights.
     * Considers success rate, cost, latency, and circuit breaker status.
     */
    ChannelConfig selectChannel(String payMethod, BigDecimal amount);

    /**
     * Record a channel transaction result for metrics tracking.
     */
    void recordResult(String channelId, boolean success, long latencyMs);

    /**
     * Check and update circuit breaker status for a channel.
     */
    void checkCircuitBreaker(String channelId);

    /**
     * Reset circuit breaker for a channel (half-open -> closed on success).
     */
    void resetCircuitBreaker(String channelId);
}
