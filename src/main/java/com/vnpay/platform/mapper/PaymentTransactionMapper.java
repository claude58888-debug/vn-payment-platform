package com.vnpay.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vnpay.platform.entity.PaymentTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentTransactionMapper extends BaseMapper<PaymentTransaction> {

    @Select("SELECT * FROM payment_transaction WHERE status = 'PROCESSING' " +
            "AND created_at >= #{since} ORDER BY created_at ASC LIMIT #{limit}")
    List<PaymentTransaction> findProcessingTransactions(@Param("since") LocalDateTime since,
                                                         @Param("limit") int limit);

    @Select("SELECT * FROM payment_transaction WHERE merchant_id = #{merchantId} " +
            "AND amount = #{amount} AND status IN ('PROCESSING', 'CREATED') " +
            "AND created_at >= #{since} ORDER BY created_at DESC")
    List<PaymentTransaction> findCandidatesForMatch(@Param("merchantId") String merchantId,
                                                     @Param("amount") BigDecimal amount,
                                                     @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM payment_transaction " +
            "WHERE merchant_id = #{merchantId} AND status = 'SUCCESS_CONFIRMED' " +
            "AND DATE(created_at) = #{date}")
    BigDecimal sumConfirmedAmountByDate(@Param("merchantId") String merchantId,
                                        @Param("date") String date);
}
