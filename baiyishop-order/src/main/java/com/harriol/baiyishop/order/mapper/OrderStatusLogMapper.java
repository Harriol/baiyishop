package com.harriol.baiyishop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.order.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;

/** 订单状态流转留痕数据访问。 */
@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {
}
