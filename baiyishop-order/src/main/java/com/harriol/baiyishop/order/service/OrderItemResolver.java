package com.harriol.baiyishop.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.order.client.ProductClient;
import com.harriol.baiyishop.order.config.OrderProperties;
import com.harriol.baiyishop.order.dto.OrderLine;
import com.harriol.baiyishop.order.dto.SkuSnapshot;
import com.harriol.baiyishop.order.entity.CartItem;
import com.harriol.baiyishop.order.mapper.CartItemMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把「购物车结算」与「立即购买」归一成同一份下单明细（docs/architecture.md 5.1 要点 7）。
 * <p>这里做的都是**事务外的只读校验**：商品是否可售、数量是否超限。
 * 金额一律按服务端读到的快照单价计算，不信任前端传值（REQ-701 约定 6）。
 */
@Service
public class OrderItemResolver {

    private final CartItemMapper cartItemMapper;
    private final ProductClient productClient;
    private final OrderProperties properties;

    public OrderItemResolver(CartItemMapper cartItemMapper, ProductClient productClient, OrderProperties properties) {
        this.cartItemMapper = cartItemMapper;
        this.productClient = productClient;
        this.properties = properties;
    }

    /** 购物车结算：只结算勾选且有效的条目，商品失效直接拒绝（REQ-602） */
    public List<OrderLine> fromCart(long userId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new BizException(ErrorCode.CART_EMPTY_CHECKED);
        }
        List<CartItem> items = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getId, cartItemIds));
        if (items.size() != cartItemIds.stream().distinct().count()) {
            // 购物车可能已被其它端改动，让用户刷新而不是拿旧数据下单
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND, "购物车已变化，请刷新后重新结算");
        }

        Map<Long, SkuSnapshot> skus = productClient.skus(items.stream().map(CartItem::getSkuId).toList());
        List<OrderLine> lines = new ArrayList<>(items.size());
        for (CartItem item : items) {
            SkuSnapshot sku = skus.get(item.getSkuId());
            if (sku == null || !sku.sellable()) {
                throw new BizException(ErrorCode.CART_ITEM_INVALID);
            }
            requireQuantityAllowed(item.getQuantity());
            lines.add(new OrderLine(sku, item.getQuantity(), item.getId()));
        }
        return lines;
    }

    /** 立即购买：单品下单，不经过购物车 */
    public List<OrderLine> buyNow(long skuId, int quantity) {
        requireQuantityAllowed(quantity);
        SkuSnapshot sku = productClient.sku(skuId);
        if (!sku.sellable()) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SALE);
        }
        return List.of(new OrderLine(sku, quantity, null));
    }

    private void requireQuantityAllowed(int quantity) {
        if (quantity < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "购买数量至少为 1");
        }
        if (quantity > properties.maxQuantityPerSku()) {
            throw new BizException(ErrorCode.CART_QUANTITY_EXCEEDED,
                    "单品限购 " + properties.maxQuantityPerSku() + " 件");
        }
    }
}
