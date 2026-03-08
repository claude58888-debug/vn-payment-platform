package com.vnpay.platform.service;

import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.entity.Merchant;

import java.util.Map;

public interface MerchantService {

    ApiResponse<Merchant> createMerchant(Map<String, Object> params);

    ApiResponse<Merchant> getMerchantInfo(String merchantId);

    Merchant getByMerchantId(String merchantId);

    boolean validateMerchantSignature(String merchantId, String data, String signature);
}
