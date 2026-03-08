package com.vnpay.platform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum MatchStatus {

    UNMATCHED("UNMATCHED", "Not yet matched"),
    MATCHED("MATCHED", "Matched to a transaction"),
    MULTI_CANDIDATES("MULTI_CANDIDATES", "Multiple candidate transactions"),
    NO_MATCH("NO_MATCH", "No matching transaction found");

    @EnumValue
    private final String code;
    private final String description;

    MatchStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
