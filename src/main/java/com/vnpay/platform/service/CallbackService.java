package com.vnpay.platform.service;

import com.vnpay.platform.entity.PaymentTransaction;

public interface CallbackService {

    /**
     * Send callback notification to merchant for a transaction status change.
     * Supports two-stage: early callback on SUCCESS_PENDING_RECON,
     * final callback on SUCCESS_CONFIRMED/FAILED.
     */
    void sendCallback(PaymentTransaction transaction);

    /**
     * Retry failed callback with exponential backoff.
     * Schedule: 0s, 30s, 2min, 10min, 1h.
     */
    void retryCallback(String transactionId);

    /**
     * Process callback confirmation from merchant (acknowledge receipt).
     */
    void confirmCallback(String transactionId, String merchantId);
}
