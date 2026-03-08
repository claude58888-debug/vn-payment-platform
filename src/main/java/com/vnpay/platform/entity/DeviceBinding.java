package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("device_binding")
public class DeviceBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private String deviceId;

    private String deviceToken;

    private String authKey;

    private String deviceInfo;

    private String trustLevel;

    private BigDecimal matchAccuracy;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
