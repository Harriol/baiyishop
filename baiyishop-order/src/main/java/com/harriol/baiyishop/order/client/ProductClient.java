package com.harriol.baiyishop.order.client;

import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.order.dto.SkuSnapshot;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品域内部调用（docs/architecture.md 4.1）。
 * <p>下单与购物车只读快照，失败即抛业务异常：宁可让用户重试，
 * 也不能在商品信息缺失的情况下继续生成订单。
 */
@Component
public class ProductClient {

    private static final String SERVICE = "http://baiyishop-product";

    private final InternalApiClient client;

    public ProductClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    /** 单个 SKU 快照；不存在时 product 侧返回 30007，原样上抛 */
    public SkuSnapshot sku(long skuId) {
        return client.get("/internal/products/skus/" + skuId, SkuSnapshot.class);
    }

    /** 批量 SKU 快照，按 skuId 建索引；查不到的 SKU 不在结果里 */
    public Map<Long, SkuSnapshot> skus(Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        String query = String.join(",", skuIds.stream().distinct().map(String::valueOf).toList());
        List<SkuSnapshot> snapshots = client.get("/internal/products/skus?skuIds=" + query,
                new TypeReference<List<SkuSnapshot>>() {
                });
        Map<Long, SkuSnapshot> map = new HashMap<>();
        if (snapshots != null) {
            snapshots.forEach(snapshot -> map.put(snapshot.skuId(), snapshot));
        }
        return map;
    }
}
