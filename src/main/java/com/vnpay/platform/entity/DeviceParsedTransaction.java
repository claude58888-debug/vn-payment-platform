package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("device_parsed_transaction")
public class DeviceParsedTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String notificationId;

    private String merchantId;

    private String deviceId;

    private String bankPackage;

    private String bankLabel;

    private String direction;

    private BigDecimal amount;

    private String currency;

    private String cardLast4;

    private LocalDateTime txTime;

    private String rawText;

    private String matchStatus;

    private String matchedTransactionId;

    private LocalDateTime createdAt;
}
