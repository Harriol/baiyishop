package com.harriol.baiyishop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.inventory.entity.StockAlert;
import org.apache.ibatis.annotations.Mapper;

/** 库存预警数据访问。 */
@Mapper
public interface StockAlertMapper extends BaseMapper<StockAlert> {
}