package com.harriol.baiyishop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;

/** 商品 SKU 数据访问。 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {
}