package com.vnpay.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vnpay.platform.entity.ChannelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChannelConfigMapper extends BaseMapper<ChannelConfig> {

    @Select("SELECT * FROM channel_config WHERE status = 'ACTIVE' AND circuit_breaker_status != 'OPEN' " +
            "ORDER BY weight DESC, success_rate DESC")
    List<ChannelConfig> findActiveChannels();

    @Update("UPDATE channel_config SET circuit_breaker_status = #{status}, " +
            "updated_at = NOW() WHERE channel_id = #{channelId}")
    int updateCircuitBreakerStatus(@Param("channelId") String channelId, @Param("status") String status);

    @Update("UPDATE channel_config SET success_rate = #{successRate}, avg_latency_ms = #{latencyMs}, " +
            "updated_at = NOW() WHERE channel_id = #{channelId}")
    int updateChannelMetrics(@Param("channelId") String channelId,
                              @Param("successRate") double successRate,
                              @Param("latencyMs") int latencyMs);
}
