package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("merchant_vnd_account")
public class MerchantVndAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private BigDecimal balanceVnd;

    private BigDecimal pendingSettlementVnd;

    private BigDecimal settledAmountVnd;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
