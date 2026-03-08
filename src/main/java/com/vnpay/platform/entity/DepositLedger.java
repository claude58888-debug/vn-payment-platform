package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("deposit_ledger")
public class DepositLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private String depositOrderId;

    private String ledgerType;

    private BigDecimal amountUsdt;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private String remark;

    private LocalDateTime createdAt;
}
