package com.vnpay.platform.service;

import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.PaymentTransaction;

import java.math.BigDecimal;

public interface RiskControlService {

    /**
     * Pre-transaction risk check. Returns true if transaction is allowed.
     */
    boolean preTransactionCheck(Merchant merchant, BigDecimal amount, String payMethod,
                                 String clientIp);

    /**
     * Post-transaction risk analysis.
     */
    void postTransactionAnalysis(PaymentTransaction transaction);

    /**
     * Check merchant's risk equity and adjust limits if needed.
     * Low risk_equity_vnd -> reduce limits or suspend.
     */
    void checkRiskEquity(String merchantId);

    /**
     * Device trust assessment: check if device notification matches bank records.
     */
    void assessDeviceTrust(String deviceId, boolean matched);

    /**
     * AML check on deposit source address.
     */
    boolean amlCheck(String depositAddress, String network);
}
