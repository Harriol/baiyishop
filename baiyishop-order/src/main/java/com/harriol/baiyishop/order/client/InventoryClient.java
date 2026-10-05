package com.harriol.baiyishop.order.client;

import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.order.dto.InventoryOpItem;
import com.harriol.baiyishop.order.dto.InventoryOpRequest;
import com.harriol.baiyishop.order.dto.InventoryOpResult;
import com.harriol.baiyishop.order.dto.OrderLine;
import com.harriol.baiyishop.order.dto.StockAvailable;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存域内部调用（docs/architecture.md 4.1、4.3）。
 * <p>写操作（锁定 / 扣减 / 释放）的<b>幂等键是 orderNo</b>：在下单时生成一次并复用，
 * 全局事务重试或消息重投都不会重复扣减（REQ-503）。
 * <p>库存不足时库存侧返回 40001，客户端原样上抛 —— 调用方不产生订单（REQ-701）。
 */
@Component
public class InventoryClient {

    private static final String SERVICE = "http://baiyishop-inventory";

    private final InternalApiClient client;

    public InventoryClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    /** 批量可售数量，按 skuId 建索引（购物车失效判定用，只读） */
    public Map<Long, Integer> availableBatch(Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        String query = String.join(",", skuIds.stream().distinct().map(String::valueOf).toList());
        List<StockAvailable> available = client.get("/internal/inventory/skus/available?skuIds=" + query,
                new TypeReference<List<StockAvailable>>() {
                });
        Map<Long, Integer> map = new HashMap<>();
        if (available != null) {
            available.forEach(item -> map.put(item.skuId(), item.available() == null ? 0 : item.available()));
        }
        return map;
    }

    /** 下单预占库存（REQ-502）：可售 → 锁定，库存不足返回 40001 */
    public InventoryOpResult lock(String orderNo, List<OrderLine> lines) {
        return post("/internal/inventory/lock", orderNo, toItems(lines),
                lines.isEmpty() ? null : lines.get(0).sku().productId());
    }

    /** 支付成功扣减库存（REQ-503）：锁定 → 出库 */
    public InventoryOpResult deduct(String orderNo, List<InventoryOpItem> items) {
        return post("/internal/inventory/deduct", orderNo, items, null);
    }

    /** 取消 / 超时释放（REQ-503、REQ-705）：锁定 → 可售 */
    public InventoryOpResult release(String orderNo, List<InventoryOpItem> items) {
        return post("/internal/inventory/release", orderNo, items, null);
    }

    private List<InventoryOpItem> toItems(List<OrderLine> lines) {
        return lines.stream()
                .map(line -> new InventoryOpItem(line.sku().skuId(), line.quantity()))
                .toList();
    }

    private InventoryOpResult post(String path, String orderNo, List<InventoryOpItem> items, Long productId) {
        InventoryOpResult result = client.post(path, new InventoryOpRequest(orderNo, productId, items),
                InventoryOpResult.class);
        return result == null ? new InventoryOpResult(true, false, List.of()) : result;
    }
}
