package com.vnpay.platform.controller;

import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /**
     * GET /api/settlement/summary
     * Get settlement summary for a merchant.
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getSettlementSummary(
            @RequestParam String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return settlementService.getSettlementSummary(merchantId, startDate, endDate);
    }
}
