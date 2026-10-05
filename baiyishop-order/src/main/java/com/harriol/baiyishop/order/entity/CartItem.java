package com.harriol.baiyishop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 购物车条目（docs/database.md 6.1，REQ-601）。
 * <p>{@code uk_user_sku} 既保证「同一 SKU 只有一行」，也让「重复加入 = 数量累加」
 * 一次 SQL 完成（INSERT ... ON DUPLICATE KEY UPDATE），不必先查后改。
 * <p>本表没有逻辑删除：条目消失就是物理删除，与订单快照互不影响。
 */
@Getter
@Setter
@TableName("cart_item")
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long skuId;

    /** 冗余商品 ID，便于批量取快照 */
    private Long productId;

    private Integer quantity;

    /** 是否勾选（参与结算） */
    private Boolean checked;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
