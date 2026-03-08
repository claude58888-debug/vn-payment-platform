package com.vnpay.platform.controller;

import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.entity.DepositOrder;
import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.MerchantUsdtAccount;
import com.vnpay.platform.service.DepositService;
import com.vnpay.platform.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final DepositService depositService;

    /**
     * POST /api/merchant/create
     * Create a new merchant with associated accounts.
     */
    @PostMapping("/create")
    public ApiResponse<Merchant> createMerchant(@RequestBody Map<String, Object> params) {
        log.info("Create merchant request: {}", params.get("merchantName"));
        return merchantService.createMerchant(params);
    }

    /**
     * GET /api/merchant/info
     * Get merchant information by merchant ID.
     */
    @GetMapping("/info")
    public ApiResponse<Merchant> getMerchantInfo(@RequestParam String merchantId) {
        return merchantService.getMerchantInfo(merchantId);
    }

    /**
     * POST /api/merchant/deposit/usdt/create
     * Create a USDT deposit order for the merchant.
     */
    @PostMapping("/deposit/usdt/create")
    public ApiResponse<DepositOrder> createUsdtDeposit(@RequestBody Map<String, Object> params) {
        String merchantId = (String) params.get("merchantId");
        BigDecimal amount = new BigDecimal(params.get("amount").toString());
        String network = (String) params.get("network");

        log.info("USDT deposit request: merchantId={}, amount={}, network={}",
                merchantId, amount, network);
        return depositService.createDeposit(merchantId, amount, network);
    }

    /**
     * GET /api/merchant/deposit/usdt/status
     * Get USDT deposit order status.
     */
    @GetMapping("/deposit/usdt/status")
    public ApiResponse<DepositOrder> getDepositStatus(@RequestParam String depositOrderId) {
        return depositService.getDepositStatus(depositOrderId);
    }

    /**
     * POST /api/merchant/deposit/usdt/withdraw
     * Withdraw USDT from merchant account.
     */
    @PostMapping("/deposit/usdt/withdraw")
    public ApiResponse<Map<String, Object>> withdrawUsdt(@RequestBody Map<String, Object> params) {
        String merchantId = (String) params.get("merchantId");
        BigDecimal amount = new BigDecimal(params.get("amount").toString());
        String toAddress = (String) params.get("toAddress");

        log.info("USDT withdraw request: merchantId={}, amount={}", merchantId, amount);
        return depositService.withdrawUsdt(merchantId, amount, toAddress);
    }
}
