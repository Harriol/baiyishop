package com.harriol.baiyishop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.order.entity.OrderRequest;
import org.apache.ibatis.annotations.Mapper;

/** 下单请求幂等表数据访问。 */
@Mapper
public interface OrderRequestMapper extends BaseMapper<OrderRequest> {
}
