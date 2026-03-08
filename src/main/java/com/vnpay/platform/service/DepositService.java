package com.vnpay.platform.service;

import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.entity.DepositOrder;
import com.vnpay.platform.entity.MerchantUsdtAccount;

import java.math.BigDecimal;
import java.util.Map;

public interface DepositService {

    ApiResponse<DepositOrder> createDeposit(String merchantId, BigDecimal amountUsdt, String network);

    ApiResponse<DepositOrder> getDepositStatus(String depositOrderId);

    ApiResponse<MerchantUsdtAccount> getUsdtBalance(String merchantId);

    ApiResponse<Map<String, Object>> withdrawUsdt(String merchantId, BigDecimal amountUsdt, String toAddress);

    void confirmDeposit(String depositOrderId, String txHash, BigDecimal actualAmount);
}
