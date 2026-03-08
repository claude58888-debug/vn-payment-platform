package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum DepositStatus {

    PENDING("PENDING", "Deposit pending"),
    CONFIRMED("CONFIRMED", "Deposit confirmed"),
    EXPIRED("EXPIRED", "Deposit expired"),
    FAILED("FAILED", "Deposit failed");

    @EnumValue
    private final String code;
    private final String description;

    DepositStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
