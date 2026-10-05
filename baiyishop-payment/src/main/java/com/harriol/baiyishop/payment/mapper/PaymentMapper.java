package com.harriol.baiyishop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/** 支付单数据访问。 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
