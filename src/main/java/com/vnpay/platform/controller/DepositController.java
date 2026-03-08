package com.vnpay.platform.controller;

import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.entity.MerchantUsdtAccount;
import com.vnpay.platform.service.DepositService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/deposit")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;

    /**
     * GET /api/deposit/usdt/balance
     * Get USDT balance for a merchant.
     */
    @GetMapping("/usdt/balance")
    public ApiResponse<MerchantUsdtAccount> getUsdtBalance(@RequestParam String merchantId) {
        return depositService.getUsdtBalance(merchantId);
    }
}
