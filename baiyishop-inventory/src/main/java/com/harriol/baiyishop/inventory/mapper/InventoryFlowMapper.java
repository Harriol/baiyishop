package com.harriol.baiyishop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.inventory.entity.InventoryFlow;
import org.apache.ibatis.annotations.Mapper;

/** 库存流水数据访问。 */
@Mapper
public interface InventoryFlowMapper extends BaseMapper<InventoryFlow> {
}