package com.harriol.baiyishop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/** 商品 SPU 数据访问。 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}