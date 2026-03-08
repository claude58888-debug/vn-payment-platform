package com.vnpay.platform.service;

import java.math.BigDecimal;

public interface LedgerService {

    /**
     * Record a payment ledger entry (double-entry bookkeeping).
     */
    void recordPayment(String merchantId, String transactionId, BigDecimal amount,
                        BigDecimal feeAmount);

    /**
     * Record a refund ledger entry.
     */
    void recordRefund(String merchantId, String transactionId, BigDecimal amount);

    /**
     * Record a fee ledger entry.
     */
    void recordFee(String merchantId, String transactionId, BigDecimal amount);

    /**
     * Record a settlement ledger entry.
     */
    void recordSettlement(String merchantId, String batchId, BigDecimal amount);

    /**
     * Record a USDT deposit ledger entry.
     */
    void recordDeposit(String merchantId, String depositOrderId, BigDecimal amountUsdt,
                        BigDecimal balanceBefore, BigDecimal balanceAfter);

    /**
     * Record a USDT withdrawal ledger entry.
     */
    void recordWithdraw(String merchantId, String depositOrderId, BigDecimal amountUsdt,
                         BigDecimal balanceBefore, BigDecimal balanceAfter);
}
