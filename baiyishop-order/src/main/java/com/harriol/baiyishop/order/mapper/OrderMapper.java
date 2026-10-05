package com.harriol.baiyishop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单数据访问。
 * <p>状态流转不用「先查后改」，一律走服务层的条件更新（WHERE status = 期望值），
 * 靠影响行数判定成败。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
