package com.vnpay.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.common.BusinessException;
import com.vnpay.platform.common.HmacUtil;
import com.vnpay.platform.common.SnowflakeIdGenerator;
import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.MerchantUsdtAccount;
import com.vnpay.platform.entity.MerchantVndAccount;
import com.vnpay.platform.mapper.MerchantMapper;
import com.vnpay.platform.mapper.MerchantUsdtAccountMapper;
import com.vnpay.platform.mapper.MerchantVndAccountMapper;
import com.vnpay.platform.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantMapper merchantMapper;
    private final MerchantUsdtAccountMapper usdtAccountMapper;
    private final MerchantVndAccountMapper vndAccountMapper;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    @Transactional
    public ApiResponse<Merchant> createMerchant(Map<String, Object> params) {
        String merchantName = (String) params.get("merchantName");
        if (merchantName == null || merchantName.isEmpty()) {
            return ApiResponse.badRequest("merchantName is required");
        }

        // Generate merchant ID and secret
        String merchantId = "M" + idGenerator.nextStringId();
        String merchantSecret = UUID.randomUUID().toString().replace("-", "");

        Merchant merchant = new Merchant();
        merchant.setMerchantId(merchantId);
        merchant.setMerchantName(merchantName);
        merchant.setMerchantSecret(merchantSecret);
        merchant.setContactName((String) params.get("contactName"));
        merchant.setContactPhone((String) params.get("contactPhone"));
        merchant.setContactEmail((String) params.get("contactEmail"));
        merchant.setNotifyUrl((String) params.get("notifyUrl"));
        merchant.setStatus("ACTIVE");

        if (params.get("feeRate") != null) {
            merchant.setFeeRate(new BigDecimal(params.get("feeRate").toString()));
        }
        if (params.get("settlementCycle") != null) {
            merchant.setSettlementCycle((String) params.get("settlementCycle"));
        }

        merchantMapper.insert(merchant);

        // Create USDT account
        MerchantUsdtAccount usdtAccount = new MerchantUsdtAccount();
        usdtAccount.setMerchantId(merchantId);
        usdtAccount.setUsdtBalance(BigDecimal.ZERO);
        usdtAccount.setUsdtFrozen(BigDecimal.ZERO);
        usdtAccount.setMarkPriceUsdtVnd(new BigDecimal("25000"));
        usdtAccount.setRiskEquityVnd(BigDecimal.ZERO);
        usdtAccountMapper.insert(usdtAccount);

        // Create VND account
        MerchantVndAccount vndAccount = new MerchantVndAccount();
        vndAccount.setMerchantId(merchantId);
        vndAccount.setBalanceVnd(BigDecimal.ZERO);
        vndAccount.setPendingSettlementVnd(BigDecimal.ZERO);
        vndAccount.setSettledAmountVnd(BigDecimal.ZERO);
        vndAccountMapper.insert(vndAccount);

        log.info("Created merchant: {}, name: {}", merchantId, merchantName);
        return ApiResponse.success(merchant);
    }

    @Override
    public ApiResponse<Merchant> getMerchantInfo(String merchantId) {
        Merchant merchant = getByMerchantId(merchantId);
        if (merchant == null) {
            return ApiResponse.notFound("Merchant not found: " + merchantId);
        }
        // Mask secret for security
        merchant.setMerchantSecret("****" + merchant.getMerchantSecret()
                .substring(merchant.getMerchantSecret().length() - 4));
        return ApiResponse.success(merchant);
    }

    @Override
    public Merchant getByMerchantId(String merchantId) {
        return merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getMerchantId, merchantId));
    }

    @Override
    public boolean validateMerchantSignature(String merchantId, String data, String signature) {
        Merchant merchant = getByMerchantId(merchantId);
        if (merchant == null) {
            throw new BusinessException(404, "Merchant not found: " + merchantId);
        }
        return HmacUtil.verify(data, merchant.getMerchantSecret(), signature);
    }
}
