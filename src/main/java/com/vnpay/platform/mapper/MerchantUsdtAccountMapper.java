package com.vnpay.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vnpay.platform.entity.MerchantUsdtAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface MerchantUsdtAccountMapper extends BaseMapper<MerchantUsdtAccount> {

    @Update("UPDATE merchant_usdt_account SET usdt_balance = usdt_balance + #{amount}, " +
            "risk_equity_vnd = (usdt_balance + #{amount} - usdt_frozen) * mark_price_usdt_vnd, " +
            "updated_at = NOW() WHERE merchant_id = #{merchantId} AND usdt_balance + #{amount} >= 0")
    int updateBalance(@Param("merchantId") String merchantId, @Param("amount") BigDecimal amount);

    @Update("UPDATE merchant_usdt_account SET usdt_frozen = usdt_frozen + #{amount}, " +
            "risk_equity_vnd = (usdt_balance - usdt_frozen - #{amount}) * mark_price_usdt_vnd, " +
            "updated_at = NOW() WHERE merchant_id = #{merchantId} AND usdt_frozen + #{amount} >= 0 " +
            "AND usdt_balance - usdt_frozen - #{amount} >= 0")
    int updateFrozen(@Param("merchantId") String merchantId, @Param("amount") BigDecimal amount);
}
