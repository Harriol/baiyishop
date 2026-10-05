package com.harriol.baiyishop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.order.entity.OrderNote;
import org.apache.ibatis.annotations.Mapper;

/** 后台订单备注数据访问。 */
@Mapper
public interface OrderNoteMapper extends BaseMapper<OrderNote> {
}
