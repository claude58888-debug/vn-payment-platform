package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_transaction")
public class PaymentTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String transactionId;

    private String merchantId;

    private String merchantOrderId;

    private BigDecimal amount;

    private String currency;

    private String payMethod;

    private String channelId;

    private String channelTransactionId;

    private String status;

    private String matchSource;

    private String notifyUrl;

    private String callbackStatus;

    private Integer callbackCount;

    private String extra;

    private BigDecimal feeAmount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
