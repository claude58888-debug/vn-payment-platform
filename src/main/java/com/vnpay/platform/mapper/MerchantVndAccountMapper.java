package com.vnpay.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vnpay.platform.entity.MerchantVndAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface MerchantVndAccountMapper extends BaseMapper<MerchantVndAccount> {

    @Update("UPDATE merchant_vnd_account SET balance_vnd = balance_vnd + #{amount}, " +
            "updated_at = NOW() WHERE merchant_id = #{merchantId}")
    int addBalance(@Param("merchantId") String merchantId, @Param("amount") BigDecimal amount);

    @Update("UPDATE merchant_vnd_account SET pending_settlement_vnd = pending_settlement_vnd + #{amount}, " +
            "updated_at = NOW() WHERE merchant_id = #{merchantId}")
    int addPendingSettlement(@Param("merchantId") String merchantId, @Param("amount") BigDecimal amount);
}
