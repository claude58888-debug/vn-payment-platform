package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("channel_config")
public class ChannelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channelId;

    private String channelName;

    private String channelType;

    private String apiBaseUrl;

    private String apiKey;

    private String apiSecret;

    private Integer weight;

    private BigDecimal successRate;

    private Integer avgLatencyMs;

    private BigDecimal feeRate;

    private Boolean hasQueryApi;

    private Boolean hasWebhook;

    private Boolean hasReconFile;

    private Integer circuitBreakerThreshold;

    private String circuitBreakerStatus;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
