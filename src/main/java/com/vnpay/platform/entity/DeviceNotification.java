package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_notification")
public class DeviceNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String notificationId;

    private String merchantId;

    private String deviceId;

    private String bankPackage;

    private String bankLabel;

    private String rawTitle;

    private String rawText;

    private String signature;

    private LocalDateTime sentAt;

    private LocalDateTime receivedAt;
}
