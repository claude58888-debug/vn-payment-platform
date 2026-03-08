package com.vnpay.platform.service;

import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.entity.PaymentTransaction;

import java.util.Map;

public interface PaymentService {

    ApiResponse<Map<String, Object>> createPayment(Map<String, Object> params);

    ApiResponse<PaymentTransaction> getPaymentStatus(String transactionId, String merchantId);

    ApiResponse<Map<String, Object>> refundPayment(String transactionId, String merchantId, String reason);

    void updateTransactionStatus(String transactionId, String status, String matchSource);

    PaymentTransaction getByTransactionId(String transactionId);
}
