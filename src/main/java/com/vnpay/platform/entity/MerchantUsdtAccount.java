package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("merchant_usdt_account")
public class MerchantUsdtAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private BigDecimal usdtBalance;

    private BigDecimal usdtFrozen;

    private String walletType;

    private String walletRef;

    private BigDecimal markPriceUsdtVnd;

    private BigDecimal riskEquityVnd;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
