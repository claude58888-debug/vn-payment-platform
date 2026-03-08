package com.vnpay.platform.service;

import com.vnpay.platform.common.BusinessException;
import com.vnpay.platform.entity.ChannelConfig;
import com.vnpay.platform.mapper.ChannelConfigMapper;
import com.vnpay.platform.service.impl.RoutingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private ChannelConfigMapper channelConfigMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private RoutingServiceImpl routingService;

    private ChannelConfig channel1;
    private ChannelConfig channel2;

    @BeforeEach
    void setUp() {
        channel1 = new ChannelConfig();
        channel1.setChannelId("CH001");
        channel1.setChannelName("Channel A");
        channel1.setWeight(80);
        channel1.setSuccessRate(new BigDecimal("0.95"));
        channel1.setCircuitBreakerThreshold(5);
        channel1.setCircuitBreakerStatus("CLOSED");
        channel1.setStatus("ACTIVE");

        channel2 = new ChannelConfig();
        channel2.setChannelId("CH002");
        channel2.setChannelName("Channel B");
        channel2.setWeight(20);
        channel2.setSuccessRate(new BigDecimal("0.80"));
        channel2.setCircuitBreakerThreshold(3);
        channel2.setCircuitBreakerStatus("CLOSED");
        channel2.setStatus("ACTIVE");
    }

    @Test
    void selectChannel_noChannelsAvailable_throwsBusinessException() {
        when(channelConfigMapper.findActiveChannels()).thenReturn(Collections.emptyList());

        assertThrows(BusinessException.class, () ->
                routingService.selectChannel("bank_transfer", new BigDecimal("100000")));
    }

    @Test
    void selectChannel_singleChannel_returnsIt() {
        when(channelConfigMapper.findActiveChannels()).thenReturn(List.of(channel1));

        ChannelConfig selected = routingService.selectChannel("bank_transfer", new BigDecimal("100000"));

        assertNotNull(selected);
        assertEquals("CH001", selected.getChannelId());
    }

    @Test
    void selectChannel_multipleChannels_returnsOneOfThem() {
        List<ChannelConfig> channels = Arrays.asList(channel1, channel2);
        when(channelConfigMapper.findActiveChannels()).thenReturn(channels);

        ChannelConfig selected = routingService.selectChannel("bank_transfer", new BigDecimal("100000"));

        assertNotNull(selected);
        assertTrue("CH001".equals(selected.getChannelId()) || "CH002".equals(selected.getChannelId()));
    }

    @Test
    void selectChannel_zeroWeightChannels_returnsFallback() {
        channel1.setWeight(0);
        channel1.setSuccessRate(BigDecimal.ZERO);
        when(channelConfigMapper.findActiveChannels()).thenReturn(List.of(channel1));

        ChannelConfig selected = routingService.selectChannel("bank_transfer", new BigDecimal("100000"));

        assertNotNull(selected);
        assertEquals("CH001", selected.getChannelId());
    }

    @Test
    void recordResult_success_incrementsMetricsAndDeletesFailKey() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        routingService.recordResult("CH001", true, 150L);

        verify(hashOperations).increment(eq("channel:metrics:CH001"), eq("success"), eq(1L));
        verify(redisTemplate).delete("channel:fail:CH001");
        verify(hashOperations).increment(eq("channel:metrics:CH001"), eq("totalLatency"), eq(150L));
        verify(hashOperations).increment(eq("channel:metrics:CH001"), eq("count"), eq(1L));
    }

    @Test
    void recordResult_failure_incrementsFailureAndChecksBreaker() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("channel:fail:CH001")).thenReturn("2");
        when(channelConfigMapper.selectOne(any())).thenReturn(channel1);

        routingService.recordResult("CH001", false, 500L);

        verify(hashOperations).increment(eq("channel:metrics:CH001"), eq("failure"), eq(1L));
        verify(valueOperations).increment("channel:fail:CH001");
    }

    @Test
    void checkCircuitBreaker_thresholdReached_opensBreaker() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("channel:fail:CH001")).thenReturn("5");
        when(channelConfigMapper.selectOne(any())).thenReturn(channel1);

        routingService.checkCircuitBreaker("CH001");

        verify(channelConfigMapper).updateCircuitBreakerStatus("CH001", "OPEN");
    }

    @Test
    void checkCircuitBreaker_belowThreshold_doesNotOpen() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("channel:fail:CH001")).thenReturn("2");
        when(channelConfigMapper.selectOne(any())).thenReturn(channel1);

        routingService.checkCircuitBreaker("CH001");

        verify(channelConfigMapper, never()).updateCircuitBreakerStatus(anyString(), anyString());
    }

    @Test
    void resetCircuitBreaker_closesAndDeletesFailKey() {
        routingService.resetCircuitBreaker("CH001");

        verify(channelConfigMapper).updateCircuitBreakerStatus("CH001", "CLOSED");
        verify(redisTemplate).delete("channel:fail:CH001");
    }
}
