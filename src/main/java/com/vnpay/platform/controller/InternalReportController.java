package com.vnpay.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.entity.ChannelConfig;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.entity.ReconciliationRecord;
import com.vnpay.platform.mapper.ChannelConfigMapper;
import com.vnpay.platform.mapper.PaymentTransactionMapper;
import com.vnpay.platform.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/internal/report")
@RequiredArgsConstructor
public class InternalReportController {

    private final PaymentTransactionMapper transactionMapper;
    private final ChannelConfigMapper channelConfigMapper;
    private final ReconciliationService reconciliationService;

    /**
     * GET /internal/report/transactions
     * Get transaction report with filtering.
     */
    @GetMapping("/transactions")
    public ApiResponse<Map<String, Object>> getTransactionReport(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {

        LambdaQueryWrapper<PaymentTransaction> query = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            query.eq(PaymentTransaction::getMerchantId, merchantId);
        }
        if (status != null) {
            query.eq(PaymentTransaction::getStatus, status);
        }
        if (channelId != null) {
            query.eq(PaymentTransaction::getChannelId, channelId);
        }
        if (startDate != null) {
            query.ge(PaymentTransaction::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.le(PaymentTransaction::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }
        query.orderByDesc(PaymentTransaction::getCreatedAt);

        Page<PaymentTransaction> pageResult = transactionMapper.selectPage(
                new Page<>(page, size), query);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("size", size);
        result.put("pages", pageResult.getPages());

        return ApiResponse.success(result);
    }

    /**
     * GET /internal/report/channels
     * Get channel performance report.
     */
    @GetMapping("/channels")
    public ApiResponse<List<Map<String, Object>>> getChannelReport() {
        List<ChannelConfig> channels = channelConfigMapper.selectList(null);

        List<Map<String, Object>> report = channels.stream().map(ch -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("channelId", ch.getChannelId());
            entry.put("channelName", ch.getChannelName());
            entry.put("channelType", ch.getChannelType());
            entry.put("status", ch.getStatus());
            entry.put("weight", ch.getWeight());
            entry.put("successRate", ch.getSuccessRate());
            entry.put("avgLatencyMs", ch.getAvgLatencyMs());
            entry.put("feeRate", ch.getFeeRate());
            entry.put("circuitBreakerStatus", ch.getCircuitBreakerStatus());
            entry.put("hasQueryApi", ch.getHasQueryApi());
            entry.put("hasWebhook", ch.getHasWebhook());
            entry.put("hasReconFile", ch.getHasReconFile());
            return entry;
        }).collect(Collectors.toList());

        return ApiResponse.success(report);
    }

    /**
     * GET /internal/report/reconciliation
     * Get reconciliation report.
     */
    @GetMapping("/reconciliation")
    public ApiResponse<List<ReconciliationRecord>> getReconciliationReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channelId) {

        List<ReconciliationRecord> records = reconciliationService.getReconciliationRecords(
                startDate, endDate, channelId);
        return ApiResponse.success(records);
    }
}
