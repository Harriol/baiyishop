package com.harriol.baiyishop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.product.entity.ParamItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 参数项数据访问。 */
@Mapper
public interface ParamItemMapper extends BaseMapper<ParamItem> {

    /** 参数项是否被商品使用（删除前校验，REQ-204） */
    @Select("SELECT COUNT(*) FROM product_param_value WHERE param_item_id = #{paramItemId}")
    long countProductUsage(@Param("paramItemId") Long paramItemId);
}