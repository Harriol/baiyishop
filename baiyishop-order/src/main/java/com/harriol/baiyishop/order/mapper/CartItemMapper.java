package com.harriol.baiyishop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.order.entity.CartItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 购物车数据访问（docs/database.md 6.1）。 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 加入购物车：同一用户同一 SKU 已存在则数量累加。
     * <p>依赖 {@code uk_user_sku} 唯一索引 + ON DUPLICATE KEY UPDATE 一条 SQL 完成，
     * 避免「先查再改」在并发下的丢更新。
     */
    @Insert("""
            INSERT INTO cart_item (user_id, sku_id, product_id, quantity, checked)
            VALUES (#{userId}, #{skuId}, #{productId}, #{quantity}, 1)
            ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)
            """)
    int addQuantity(@Param("userId") long userId,
                    @Param("skuId") long skuId,
                    @Param("productId") long productId,
                    @Param("quantity") int quantity);
}
