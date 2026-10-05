package com.harriol.baiyishop.order.dto;

import com.harriol.baiyishop.order.entity.OrderItem;

/** 订单明细视图（下单时的商品快照）。 */
public record OrderItemView(Long productId, Long skuId, String productName, String skuName,
                            String productImage, Long unitPrice, Integer quantity, Long totalAmount) {

    public static OrderItemView from(OrderItem item) {
        return new OrderItemView(item.getProductId(), item.getSkuId(), item.getProductName(),
                item.getSkuName(), item.getProductImage(), item.getUnitPrice(), item.getQuantity(),
                item.getTotalAmount());
    }
}
