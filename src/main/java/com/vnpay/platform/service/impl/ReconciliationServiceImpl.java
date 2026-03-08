package com.vnpay.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vnpay.platform.entity.ChannelConfig;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.entity.ReconciliationRecord;
import com.vnpay.platform.mapper.ChannelConfigMapper;
import com.vnpay.platform.mapper.PaymentTransactionMapper;
import com.vnpay.platform.mapper.ReconciliationRecordMapper;
import com.vnpay.platform.service.PaymentService;
import com.vnpay.platform.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconciliationRecordMapper reconMapper;
    private final PaymentTransactionMapper transactionMapper;
    private final ChannelConfigMapper channelConfigMapper;
    private final PaymentService paymentService;

    @Override
    @Transactional
    public ReconciliationRecord reconcile(String channelId, LocalDate date) {
        log.info("Starting reconciliation for channel: {}, date: {}", channelId, date);

        ReconciliationRecord record = new ReconciliationRecord();
        record.setReconDate(date);
        record.setChannelId(channelId);
        record.setFileName(channelId + "_" + date + ".csv");
        record.setStatus("PROCESSING");

        // Find all transactions for this channel on this date
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<PaymentTransaction> transactions = transactionMapper.selectList(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getChannelId, channelId)
                        .between(PaymentTransaction::getCreatedAt, startOfDay, endOfDay)
                        .in(PaymentTransaction::getStatus,
                                "PROCESSING", "SUCCESS_PENDING_RECON", "SUCCESS_CONFIRMED", "UNKNOWN"));

        int totalRecords = transactions.size();
        int matchedRecords = 0;
        int unmatchedRecords = 0;
        int exceptionRecords = 0;

        // In production: download reconciliation file from channel (SFTP/Email/Portal)
        // and compare against our records. Here we simulate the reconciliation.
        for (PaymentTransaction tx : transactions) {
            if ("SUCCESS_PENDING_RECON".equals(tx.getStatus())) {
                // Confirmed by reconciliation -> final confirmation
                paymentService.updateTransactionStatus(
                        tx.getTransactionId(), "SUCCESS_CONFIRMED", "RECONCILIATION");
                matchedRecords++;
            } else if ("PROCESSING".equals(tx.getStatus())) {
                // Still processing in channel, check if it's been too long
                if (tx.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                    // Mark as failed if older than 24h and still processing
                    paymentService.updateTransactionStatus(
                            tx.getTransactionId(), "FAILED", "RECONCILIATION");
                    exceptionRecords++;
                } else {
                    unmatchedRecords++;
                }
            } else if ("UNKNOWN".equals(tx.getStatus())) {
                exceptionRecords++;
            } else {
                matchedRecords++;
            }
        }

        record.setTotalRecords(totalRecords);
        record.setMatchedRecords(matchedRecords);
        record.setUnmatchedRecords(unmatchedRecords);
        record.setExceptionRecords(exceptionRecords);
        record.setStatus("COMPLETED");

        reconMapper.insert(record);

        log.info("Reconciliation completed for channel: {}, date: {}. " +
                        "Total: {}, Matched: {}, Unmatched: {}, Exceptions: {}",
                channelId, date, totalRecords, matchedRecords, unmatchedRecords, exceptionRecords);

        return record;
    }

    @Override
    public List<ReconciliationRecord> reconcileAll(LocalDate date) {
        List<ChannelConfig> channels = channelConfigMapper.selectList(
                new LambdaQueryWrapper<ChannelConfig>()
                        .eq(ChannelConfig::getStatus, "ACTIVE")
                        .eq(ChannelConfig::getHasReconFile, true));

        List<ReconciliationRecord> results = new ArrayList<>();
        for (ChannelConfig channel : channels) {
            try {
                ReconciliationRecord record = reconcile(channel.getChannelId(), date);
                results.add(record);
            } catch (Exception e) {
                log.error("Reconciliation failed for channel: {}", channel.getChannelId(), e);
            }
        }

        return results;
    }

    @Override
    public List<ReconciliationRecord> getReconciliationRecords(LocalDate startDate,
                                                                LocalDate endDate,
                                                                String channelId) {
        LambdaQueryWrapper<ReconciliationRecord> query = new LambdaQueryWrapper<>();
        if (startDate != null) {
            query.ge(ReconciliationRecord::getReconDate, startDate);
        }
        if (endDate != null) {
            query.le(ReconciliationRecord::getReconDate, endDate);
        }
        if (channelId != null && !channelId.isEmpty()) {
            query.eq(ReconciliationRecord::getChannelId, channelId);
        }
        query.orderByDesc(ReconciliationRecord::getReconDate);

        return reconMapper.selectList(query);
    }
}
