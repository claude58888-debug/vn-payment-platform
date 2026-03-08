package com.vnpay.platform.service.impl;

import com.vnpay.platform.common.BusinessException;
import com.vnpay.platform.entity.ChannelConfig;
import com.vnpay.platform.mapper.ChannelConfigMapper;
import com.vnpay.platform.service.RoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingServiceImpl implements RoutingService {

    private final ChannelConfigMapper channelConfigMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String CHANNEL_FAIL_COUNT_KEY = "channel:fail:";
    private static final String CHANNEL_METRICS_KEY = "channel:metrics:";

    @Override
    public ChannelConfig selectChannel(String payMethod, BigDecimal amount) {
        List<ChannelConfig> channels = channelConfigMapper.findActiveChannels();

        if (channels.isEmpty()) {
            throw new BusinessException(503, "No available payment channels");
        }

        // Filter by pay method compatibility (simplified - in production would have more rules)
        // Weighted random selection based on weight and success rate
        int totalWeight = channels.stream()
                .mapToInt(c -> (int) (c.getWeight() * c.getSuccessRate().doubleValue()))
                .sum();

        if (totalWeight <= 0) {
            return channels.get(0); // Fallback to first available
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (ChannelConfig channel : channels) {
            cumulative += (int) (channel.getWeight() * channel.getSuccessRate().doubleValue());
            if (random < cumulative) {
                log.info("Selected channel: {} (weight={}, successRate={}) for payMethod={}, amount={}",
                        channel.getChannelId(), channel.getWeight(), channel.getSuccessRate(),
                        payMethod, amount);
                return channel;
            }
        }

        return channels.get(channels.size() - 1);
    }

    @Override
    public void recordResult(String channelId, boolean success, long latencyMs) {
        String metricsKey = CHANNEL_METRICS_KEY + channelId;

        if (success) {
            redisTemplate.opsForHash().increment(metricsKey, "success", 1);
            // Reset consecutive failures
            redisTemplate.delete(CHANNEL_FAIL_COUNT_KEY + channelId);
        } else {
            redisTemplate.opsForHash().increment(metricsKey, "failure", 1);
            redisTemplate.opsForValue().increment(CHANNEL_FAIL_COUNT_KEY + channelId);
            checkCircuitBreaker(channelId);
        }

        redisTemplate.opsForHash().increment(metricsKey, "totalLatency", latencyMs);
        redisTemplate.opsForHash().increment(metricsKey, "count", 1);
        redisTemplate.expire(metricsKey, Duration.ofHours(24));

        log.debug("Recorded channel result: channelId={}, success={}, latencyMs={}",
                channelId, success, latencyMs);
    }

    @Override
    public void checkCircuitBreaker(String channelId) {
        String failKey = CHANNEL_FAIL_COUNT_KEY + channelId;
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;

        // Get threshold from DB
        ChannelConfig channel = channelConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelConfig>()
                        .eq(ChannelConfig::getChannelId, channelId));

        if (channel != null && failCount >= channel.getCircuitBreakerThreshold()) {
            log.warn("Circuit breaker OPEN for channel: {} (failures={})", channelId, failCount);
            channelConfigMapper.updateCircuitBreakerStatus(channelId, "OPEN");

            // Schedule half-open after 60 seconds
            redisTemplate.opsForValue().set(
                    "channel:breaker:halfopen:" + channelId, "1",
                    Duration.ofSeconds(60));
        }
    }

    @Override
    public void resetCircuitBreaker(String channelId) {
        log.info("Circuit breaker CLOSED for channel: {}", channelId);
        channelConfigMapper.updateCircuitBreakerStatus(channelId, "CLOSED");
        redisTemplate.delete(CHANNEL_FAIL_COUNT_KEY + channelId);
    }
}
