package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayMethod {

    CARD("card", "Bank card payment"),
    QRCODE("qrcode", "QR code payment"),
    BANK_TRANSFER("bank_transfer", "Bank transfer");

    @EnumValue
    private final String code;
    private final String description;

    PayMethod(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
