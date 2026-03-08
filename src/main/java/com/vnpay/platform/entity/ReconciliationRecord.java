package com.vnpay.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reconciliation_record")
public class ReconciliationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate reconDate;

    private String channelId;

    private String fileName;

    private Integer totalRecords;

    private Integer matchedRecords;

    private Integer unmatchedRecords;

    private Integer exceptionRecords;

    private String status;

    private LocalDateTime createdAt;
}
