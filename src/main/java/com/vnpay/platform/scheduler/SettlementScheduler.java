package com.vnpay.platform.scheduler;

import com.vnpay.platform.entity.SettlementBatch;
import com.vnpay.platform.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled settlement task.
 * Daily EOD: calculate settleable amount = all SUCCESS_CONFIRMED - fees - risk reserve
 * Risk check -> generate settlement batch -> call bank/NAPAS for disbursement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;

    /**
     * Run daily settlement at 23:00 (11 PM) for today's confirmed transactions.
     * This runs after reconciliation has confirmed transactions.
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void dailySettlement() {
        LocalDate today = LocalDate.now();
        log.info("Starting daily settlement for date: {}", today);

        try {
            List<SettlementBatch> batches = settlementService.processAllSettlements(today);

            BigDecimal totalNetAmount = batches.stream()
                    .map(SettlementBatch::getNetAmountVnd)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.info("Daily settlement completed for {}. Batches: {}, Total net amount: {} VND",
                    today, batches.size(), totalNetAmount);

        } catch (Exception e) {
            log.error("Daily settlement failed for date: {}", today, e);
        }
    }

    /**
     * Process previous day settlement if missed (runs at 6 AM as backup).
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void catchUpSettlement() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Running catch-up settlement check for date: {}", yesterday);

        try {
            List<SettlementBatch> batches = settlementService.processAllSettlements(yesterday);
            if (!batches.isEmpty()) {
                log.info("Catch-up settlement generated {} new batches for {}",
                        batches.size(), yesterday);
            }
        } catch (Exception e) {
            log.error("Catch-up settlement failed for date: {}", yesterday, e);
        }
    }
}
