package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum LedgerType {

    PAYMENT("PAYMENT", "Payment transaction"),
    REFUND("REFUND", "Refund transaction"),
    FEE("FEE", "Fee deduction"),
    SETTLEMENT("SETTLEMENT", "Settlement payout"),
    DEPOSIT("DEPOSIT", "USDT deposit"),
    WITHDRAW("WITHDRAW", "USDT withdrawal");

    @EnumValue
    private final String code;
    private final String description;

    LedgerType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
