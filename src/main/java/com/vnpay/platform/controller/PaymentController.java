package com.vnpay.platform.controller;

import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.service.PaymentService;
import com.vnpay.platform.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final SettlementService settlementService;

    /**
     * POST /api/payment/create
     * Create a new payment transaction.
     */
    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> createPayment(@RequestBody Map<String, Object> params) {
        log.info("Payment create request: merchantId={}, amount={}",
                params.get("merchantId"), params.get("amount"));
        return paymentService.createPayment(params);
    }

    /**
     * GET /api/payment/status
     * Query payment transaction status.
     */
    @GetMapping("/status")
    public ApiResponse<PaymentTransaction> getPaymentStatus(
            @RequestParam String transactionId,
            @RequestParam String merchantId) {
        return paymentService.getPaymentStatus(transactionId, merchantId);
    }

    /**
     * POST /api/payment/refund
     * Initiate a refund for a payment transaction.
     */
    @PostMapping("/refund")
    public ApiResponse<Map<String, Object>> refundPayment(@RequestBody Map<String, Object> params) {
        String transactionId = (String) params.get("transactionId");
        String merchantId = (String) params.get("merchantId");
        String reason = (String) params.get("reason");

        log.info("Refund request: txId={}, merchantId={}", transactionId, merchantId);
        return paymentService.refundPayment(transactionId, merchantId, reason);
    }

    /**
     * GET /api/payment/statement
     * Get payment statement (detailed transaction list) for a merchant.
     */
    @GetMapping("/statement")
    public ApiResponse<List<Map<String, Object>>> getPaymentStatement(
            @RequestParam String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return settlementService.getPaymentStatement(merchantId, startDate, endDate);
    }
}
