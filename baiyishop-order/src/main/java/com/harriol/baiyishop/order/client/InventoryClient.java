package com.harriol.baiyishop.order.client;

import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.order.dto.StockAvailable;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存域内部调用（docs/architecture.md 4.1、4.3）。
 * <p>写操作（锁定 / 扣减 / 释放）的<b>幂等键是 orderNo</b>，在 order 侧生成并复用，
 * 保证全局事务重试或消息重投都不会重复扣减（REQ-503）。
 */
@Component
public class InventoryClient {

    private static final String SERVICE = "http://baiyishop-inventory";

    private final InternalApiClient client;

    public InventoryClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    /** 批量可售数量，按 skuId 建索引（购物车失效判定用，只读缓存数据） */
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
}
