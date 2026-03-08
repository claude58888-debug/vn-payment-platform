package com.vnpay.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vnpay.platform.entity.TransactionLedger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionLedgerMapper extends BaseMapper<TransactionLedger> {
}
