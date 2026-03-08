package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum CallbackStatus {

    PENDING("PENDING", "Callback pending"),
    SENT("SENT", "Callback sent"),
    CONFIRMED("CONFIRMED", "Callback confirmed by merchant"),
    FAILED("FAILED", "Callback failed after max retries");

    @EnumValue
    private final String code;
    private final String description;

    CallbackStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
