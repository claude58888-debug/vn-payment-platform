package com.vnpay.platform.scheduler;

import com.vnpay.platform.entity.ReconciliationRecord;
import com.vnpay.platform.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled reconciliation task.
 * Daily download reconciliation files from banks (SFTP/Email/Portal),
 * parse and match against platform transactions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    /**
     * Run daily reconciliation at 2:00 AM for the previous day's transactions.
     * Cron: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyReconciliation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Starting daily reconciliation for date: {}", yesterday);

        try {
            List<ReconciliationRecord> results = reconciliationService.reconcileAll(yesterday);

            int totalMatched = results.stream()
                    .mapToInt(ReconciliationRecord::getMatchedRecords).sum();
            int totalUnmatched = results.stream()
                    .mapToInt(ReconciliationRecord::getUnmatchedRecords).sum();
            int totalExceptions = results.stream()
                    .mapToInt(ReconciliationRecord::getExceptionRecords).sum();

            log.info("Daily reconciliation completed for {}. Channels: {}, " +
                            "Matched: {}, Unmatched: {}, Exceptions: {}",
                    yesterday, results.size(), totalMatched, totalUnmatched, totalExceptions);

        } catch (Exception e) {
            log.error("Daily reconciliation failed for date: {}", yesterday, e);
        }
    }

    /**
     * Run a catch-up reconciliation every 6 hours for transactions
     * that might have been missed.
     */
    @Scheduled(fixedDelay = 21600000, initialDelay = 3600000) // 6 hours, start after 1 hour
    public void catchUpReconciliation() {
        LocalDate today = LocalDate.now();
        log.info("Running catch-up reconciliation for date: {}", today);

        try {
            List<ReconciliationRecord> results = reconciliationService.reconcileAll(today);
            log.info("Catch-up reconciliation completed. Processed {} channels", results.size());
        } catch (Exception e) {
            log.error("Catch-up reconciliation failed for date: {}", today, e);
        }
    }
}
