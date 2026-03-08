package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum MatchSource {

    CHANNEL("CHANNEL", "Matched via upstream channel query"),
    DEVICE("DEVICE", "Matched via device notification"),
    RECONCILIATION("RECONCILIATION", "Matched via reconciliation file");

    @EnumValue
    private final String code;
    private final String description;

    MatchSource(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
