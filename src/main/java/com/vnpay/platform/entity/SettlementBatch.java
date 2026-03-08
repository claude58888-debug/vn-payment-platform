package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("settlement_batch")
public class SettlementBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;

    private String merchantId;

    private LocalDate settlementDate;

    private BigDecimal totalAmountVnd;

    private BigDecimal feeAmountVnd;

    private BigDecimal netAmountVnd;

    private BigDecimal riskReserveVnd;

    private String status;

    private String bankRef;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
