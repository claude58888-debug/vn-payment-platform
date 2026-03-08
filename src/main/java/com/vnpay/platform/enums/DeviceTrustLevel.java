package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum DeviceTrustLevel {

    NEW("NEW", "New device, auxiliary only"),
    TRUSTED("TRUSTED", "Trusted device, can trigger early confirmation"),
    SUSPICIOUS("SUSPICIOUS", "Suspicious device, under review"),
    BLOCKED("BLOCKED", "Blocked device");

    @EnumValue
    private final String code;
    private final String description;

    DeviceTrustLevel(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
