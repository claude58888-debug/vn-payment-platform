package com.vnpay.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.common.SnowflakeIdGenerator;
import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.entity.SettlementBatch;
import com.vnpay.platform.mapper.MerchantMapper;
import com.vnpay.platform.mapper.PaymentTransactionMapper;
import com.vnpay.platform.mapper.SettlementBatchMapper;
import com.vnpay.platform.service.LedgerService;
import com.vnpay.platform.service.RiskControlService;
import com.vnpay.platform.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementBatchMapper settlementMapper;
    private final PaymentTransactionMapper transactionMapper;
    private final MerchantMapper merchantMapper;
    private final LedgerService ledgerService;
    private final RiskControlService riskControlService;
    private final SnowflakeIdGenerator idGenerator;

    @Value("${platform.settlement.risk-reserve-rate:0.02}")
    private double riskReserveRate;

    @Override
    @Transactional
    public SettlementBatch generateSettlement(String merchantId, LocalDate date) {
        log.info("Generating settlement for merchant: {}, date: {}", merchantId, date);

        // Check if settlement already exists for this date
        SettlementBatch existing = settlementMapper.selectOne(
                new LambdaQueryWrapper<SettlementBatch>()
                        .eq(SettlementBatch::getMerchantId, merchantId)
                        .eq(SettlementBatch::getSettlementDate, date));
        if (existing != null) {
            log.info("Settlement already exists for merchant {} on {}", merchantId, date);
            return existing;
        }

        // Get all SUCCESS_CONFIRMED transactions for this merchant on this date
        BigDecimal totalAmount = transactionMapper.sumConfirmedAmountByDate(
                merchantId, date.toString());

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("No confirmed transactions to settle for merchant {} on {}", merchantId, date);
            return null;
        }

        // Get merchant fee rate
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getMerchantId, merchantId));

        BigDecimal feeRate = merchant != null ? merchant.getFeeRate() : new BigDecimal("0.01");
        BigDecimal feeAmount = totalAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal riskReserve = totalAmount.multiply(new BigDecimal(riskReserveRate))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = totalAmount.subtract(feeAmount).subtract(riskReserve);

        // Create settlement batch
        String batchId = "S" + idGenerator.nextStringId();
        SettlementBatch batch = new SettlementBatch();
        batch.setBatchId(batchId);
        batch.setMerchantId(merchantId);
        batch.setSettlementDate(date);
        batch.setTotalAmountVnd(totalAmount);
        batch.setFeeAmountVnd(feeAmount);
        batch.setNetAmountVnd(netAmount);
        batch.setRiskReserveVnd(riskReserve);
        batch.setStatus("PENDING");

        settlementMapper.insert(batch);

        // Record settlement ledger
        ledgerService.recordSettlement(merchantId, batchId, netAmount);

        log.info("Settlement generated: batchId={}, merchantId={}, total={}, fee={}, net={}",
                batchId, merchantId, totalAmount, feeAmount, netAmount);

        return batch;
    }

    @Override
    public List<SettlementBatch> processAllSettlements(LocalDate date) {
        List<Merchant> merchants = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getStatus, "ACTIVE"));

        List<SettlementBatch> results = new ArrayList<>();
        for (Merchant merchant : merchants) {
            try {
                SettlementBatch batch = generateSettlement(merchant.getMerchantId(), date);
                if (batch != null) {
                    results.add(batch);
                }
            } catch (Exception e) {
                log.error("Settlement failed for merchant: {}", merchant.getMerchantId(), e);
            }
        }

        return results;
    }

    @Override
    public ApiResponse<Map<String, Object>> getSettlementSummary(String merchantId,
                                                                   LocalDate startDate,
                                                                   LocalDate endDate) {
        LambdaQueryWrapper<SettlementBatch> query = new LambdaQueryWrapper<>();
        query.eq(SettlementBatch::getMerchantId, merchantId);
        if (startDate != null) {
            query.ge(SettlementBatch::getSettlementDate, startDate);
        }
        if (endDate != null) {
            query.le(SettlementBatch::getSettlementDate, endDate);
        }

        List<SettlementBatch> batches = settlementMapper.selectList(query);

        BigDecimal totalSettled = batches.stream()
                .filter(b -> "COMPLETED".equals(b.getStatus()) || "PENDING".equals(b.getStatus()))
                .map(SettlementBatch::getNetAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFees = batches.stream()
                .map(SettlementBatch::getFeeAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new HashMap<>();
        summary.put("merchantId", merchantId);
        summary.put("startDate", startDate);
        summary.put("endDate", endDate);
        summary.put("totalBatches", batches.size());
        summary.put("totalSettledVnd", totalSettled);
        summary.put("totalFeesVnd", totalFees);
        summary.put("batches", batches);

        return ApiResponse.success(summary);
    }

    @Override
    public ApiResponse<List<Map<String, Object>>> getPaymentStatement(String merchantId,
                                                                       LocalDate startDate,
                                                                       LocalDate endDate) {
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : LocalDateTime.now();

        List<PaymentTransaction> transactions = transactionMapper.selectList(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getMerchantId, merchantId)
                        .between(PaymentTransaction::getCreatedAt, start, end)
                        .orderByDesc(PaymentTransaction::getCreatedAt));

        List<Map<String, Object>> statement = transactions.stream().map(tx -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("transactionId", tx.getTransactionId());
            entry.put("merchantOrderId", tx.getMerchantOrderId());
            entry.put("amount", tx.getAmount());
            entry.put("feeAmount", tx.getFeeAmount());
            entry.put("status", tx.getStatus());
            entry.put("payMethod", tx.getPayMethod());
            entry.put("channelId", tx.getChannelId());
            entry.put("createdAt", tx.getCreatedAt());
            return entry;
        }).collect(Collectors.toList());

        return ApiResponse.success(statement);
    }
}
