package com.harriol.baiyishop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.payment.entity.PaymentCallbackLog;
import org.apache.ibatis.annotations.Mapper;

/** 支付回调日志数据访问。 */
@Mapper
public interface PaymentCallbackLogMapper extends BaseMapper<PaymentCallbackLog> {
}
