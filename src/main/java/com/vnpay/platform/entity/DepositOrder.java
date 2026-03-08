package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("deposit_order")
public class DepositOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String depositOrderId;

    private String merchantId;

    private BigDecimal amountUsdt;

    private BigDecimal actualAmountUsdt;

    private String network;

    private String depositAddress;

    private String txHash;

    private String status;

    private LocalDateTime expiredAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;
}
