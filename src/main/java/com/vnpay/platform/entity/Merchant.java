package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private String merchantName;

    private String merchantSecret;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private String notifyUrl;

    private BigDecimal feeRate;

    private String settlementCycle;

    private String riskLevel;

    private Long dailyLimitVnd;

    private Long singleLimitVnd;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
