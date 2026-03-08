package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum ChannelType {

    BANK("BANK", "Direct bank channel"),
    NAPAS("NAPAS", "NAPAS interbank network"),
    PSP("PSP", "Payment service provider");

    @EnumValue
    private final String code;
    private final String description;

    ChannelType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
