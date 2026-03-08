package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum TransactionStatus {

    CREATED("CREATED", "Order created"),
    PROCESSING("PROCESSING", "Processing payment"),
    SUCCESS_PENDING_RECON("SUCCESS_PENDING_RECON", "Success pending reconciliation"),
    SUCCESS_CONFIRMED("SUCCESS_CONFIRMED", "Success confirmed by reconciliation"),
    FAILED("FAILED", "Payment failed"),
    UNKNOWN("UNKNOWN", "Unknown status");

    @EnumValue
    private final String code;
    private final String description;

    TransactionStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
