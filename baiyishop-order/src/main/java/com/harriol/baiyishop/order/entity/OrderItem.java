package com.harriol.baiyishop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 订单明细（docs/database.md 6.3）。
 * <p>商品名 / 规格 / 图片 / 单价都是**下单时的快照**：商品之后改价或改名，
 * 历史订单展示的仍是当时的信息（REQ-701）。
 */
@Getter
@Setter
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long productId;

    private Long skuId;

    private String productName;

    private String skuName;

    private String productImage;

    /** 下单时单价（分） */
    private Long unitPrice;

    private Integer quantity;

    /** 小计（分）= unitPrice × quantity */
    private Long totalAmount;

    private LocalDateTime createdAt;
}
