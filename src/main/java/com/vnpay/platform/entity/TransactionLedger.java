package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("transaction_ledger")
public class TransactionLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ledgerId;

    private String merchantId;

    private String transactionId;

    private String ledgerType;

    private String debitAccount;

    private String creditAccount;

    private BigDecimal amount;

    private String currency;

    private String remark;

    private LocalDateTime createdAt;
}
