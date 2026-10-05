package com.harriol.baiyishop.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.order.client.InventoryClient;
import com.harriol.baiyishop.order.client.ProductClient;
import com.harriol.baiyishop.order.config.OrderProperties;
import com.harriol.baiyishop.order.dto.CartItemView;
import com.harriol.baiyishop.order.dto.CartView;
import com.harriol.baiyishop.order.dto.SkuSnapshot;
import com.harriol.baiyishop.order.entity.CartItem;
import com.harriol.baiyishop.order.mapper.CartItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 购物车（REQ-601、REQ-602）。
 * <p>三条实现要点：
 * <ol>
 *   <li><b>累加靠唯一索引</b>：同一 SKU 重复加入用 {@code ON DUPLICATE KEY UPDATE} 一条 SQL 完成，
 *       不先查后改（并发下不会丢更新）</li>
 *   <li><b>限购在累加之后校验</b>：超出上限时抛业务异常让事务回滚，累加不生效（REQ-601 的 50006）</li>
 *   <li><b>失效只标记不删除</b>：下架 / 售罄的条目保留在车里并给出原因，
 *       由前端置灰（REQ-602）；结算时再统一拦截</li>
 * </ol>
 * <p>商品与库存都是**跨服务只读**，不参与本地事务；金额一律由服务端按快照单价计算。
 */
@Service
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderProperties properties;

    public CartService(CartItemMapper cartItemMapper,
                       ProductClient productClient,
                       InventoryClient inventoryClient,
                       OrderProperties properties) {
        this.cartItemMapper = cartItemMapper;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.properties = properties;
    }

    /** 加入购物车：同 SKU 数量累加，超出单品限购则整体回滚（REQ-601） */
    @Transactional
    public CartView add(long userId, long skuId, int quantity) {
        SkuSnapshot sku = productClient.sku(skuId);
        if (!sku.sellable()) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SALE);
        }
        if (quantity > properties.maxQuantityPerSku()) {
            throw new BizException(ErrorCode.CART_QUANTITY_EXCEEDED, quantityExceededMessage());
        }
        cartItemMapper.addQuantity(userId, skuId, sku.productId(), quantity);

        CartItem saved = find(userId, skuId);
        if (saved != null && saved.getQuantity() > properties.maxQuantityPerSku()) {
            // 抛出后事务回滚，本次累加不生效
            throw new BizException(ErrorCode.CART_QUANTITY_EXCEEDED, quantityExceededMessage());
        }
        return viewOf(userId);
    }

    /** 购物车列表（含试算合计与失效标记） */
    public CartView list(long userId) {
        return viewOf(userId);
    }

    /** 修改数量 / 勾选状态（REQ-601） */
    @Transactional
    public CartView update(long userId, long itemId, Integer quantity, Boolean checked) {
        CartItem item = requireOwned(userId, itemId);
        if (quantity != null) {
            if (quantity > properties.maxQuantityPerSku()) {
                throw new BizException(ErrorCode.CART_QUANTITY_EXCEEDED, quantityExceededMessage());
            }
            item.setQuantity(quantity);
        }
        if (checked != null) {
            item.setChecked(checked);
        }
        cartItemMapper.updateById(item);
        return viewOf(userId);
    }

    /** 删除条目（REQ-601） */
    @Transactional
    public CartView delete(long userId, long itemId) {
        requireOwned(userId, itemId);
        cartItemMapper.deleteById(itemId);
        return viewOf(userId);
    }

    /** 全选 / 取消全选（REQ-601） */
    @Transactional
    public CartView checkAll(long userId, boolean checked) {
        CartItem update = new CartItem();
        update.setChecked(checked);
        cartItemMapper.update(update, Wrappers.<CartItem>lambdaUpdate().eq(CartItem::getUserId, userId));
        return viewOf(userId);
    }

    private CartView viewOf(long userId) {
        List<CartItem> items = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getId));
        if (items.isEmpty()) {
            return new CartView(List.of(), 0L, 0);
        }
        List<Long> skuIds = items.stream().map(CartItem::getSkuId).toList();
        Map<Long, SkuSnapshot> skus = productClient.skus(skuIds);
        Map<Long, Integer> available = inventoryClient.availableBatch(skuIds);

        long amount = 0L;
        int count = 0;
        List<CartItemView> views = new ArrayList<>(items.size());
        for (CartItem item : items) {
            SkuSnapshot sku = skus.get(item.getSkuId());
            String reason = invalidReasonOf(sku, available.get(item.getSkuId()), item.getQuantity());
            boolean checked = Boolean.TRUE.equals(item.getChecked());
            if (checked && reason == null) {
                amount += sku.price() * item.getQuantity();
                count += item.getQuantity();
            }
            views.add(new CartItemView(item.getId(), item.getSkuId(), item.getProductId(),
                    sku == null ? null : sku.productName(),
                    sku == null ? null : sku.image(),
                    sku == null ? null : sku.price(),
                    item.getQuantity(), checked, reason != null, reason));
        }
        return new CartView(views, amount, count);
    }

    /** 失效原因：无快照 = 商品已删除；未上架 = 已下架；库存不足 / 售罄单独提示（REQ-602） */
    private String invalidReasonOf(SkuSnapshot sku, Integer available, int quantity) {
        if (sku == null) {
            return "商品已删除";
        }
        if (!sku.sellable()) {
            return "商品已下架";
        }
        int stock = available == null ? 0 : available;
        if (stock <= 0) {
            return "已售罄";
        }
        if (stock < quantity) {
            return "库存不足（剩 " + stock + " 件）";
        }
        return null;
    }

    private CartItem find(long userId, long skuId) {
        return cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId));
    }

    private CartItem requireOwned(long userId, long itemId) {
        CartItem item = cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getId, itemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) {
            // 不区分「不存在」与「不属于你」，避免据此探测他人条目
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        return item;
    }

    private String quantityExceededMessage() {
        return "单品限购 " + properties.maxQuantityPerSku() + " 件";
    }
}
