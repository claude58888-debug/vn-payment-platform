package com.vnpay.platform.service;

import com.vnpay.platform.entity.ReconciliationRecord;

import java.time.LocalDate;
import java.util.List;

public interface ReconciliationService {

    /**
     * Run reconciliation for a specific channel and date.
     * Downloads reconciliation file, parses, and matches against platform transactions.
     */
    ReconciliationRecord reconcile(String channelId, LocalDate date);

    /**
     * Run reconciliation for all channels for a given date.
     */
    List<ReconciliationRecord> reconcileAll(LocalDate date);

    /**
     * Get reconciliation summary for internal reports.
     */
    List<ReconciliationRecord> getReconciliationRecords(LocalDate startDate, LocalDate endDate,
                                                         String channelId);
}
