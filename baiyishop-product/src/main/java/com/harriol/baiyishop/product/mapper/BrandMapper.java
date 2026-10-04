package com.harriol.baiyishop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.product.entity.Brand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 品牌数据访问。 */
@Mapper
public interface BrandMapper extends BaseMapper<Brand> {

    /** 品牌是否被商品引用（删除前校验，REQ-202） */
    @Select("SELECT COUNT(*) FROM product WHERE brand_id = #{brandId} AND deleted = 0")
    long countProducts(@Param("brandId") Long brandId);
}