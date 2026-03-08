package com.vnpay.platform.service;

import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.entity.SettlementBatch;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SettlementService {

    /**
     * Generate settlement batch for a merchant on a given date.
     * Calculates: settleable = SUM(SUCCESS_CONFIRMED) - fees - risk_reserve
     */
    SettlementBatch generateSettlement(String merchantId, LocalDate date);

    /**
     * Process all merchant settlements for a given date (EOD).
     */
    List<SettlementBatch> processAllSettlements(LocalDate date);

    /**
     * Get settlement summary for a merchant.
     */
    ApiResponse<Map<String, Object>> getSettlementSummary(String merchantId,
                                                           LocalDate startDate,
                                                           LocalDate endDate);

    /**
     * Get payment statement (detailed transaction list) for a merchant.
     */
    ApiResponse<List<Map<String, Object>>> getPaymentStatement(String merchantId,
                                                                LocalDate startDate,
                                                                LocalDate endDate);
}
