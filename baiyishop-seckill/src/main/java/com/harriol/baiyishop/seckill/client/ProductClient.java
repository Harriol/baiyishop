package com.harriol.baiyishop.seckill.client;

import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.seckill.dto.SkuSnapshot;
import org.springframework.stereotype.Component;

/** 商品域内部调用：建活动时取原价与商品名，抢购时校验商品仍可售。 */
@Component
public class ProductClient {

    private static final String SERVICE = "http://baiyishop-product";

    private final InternalApiClient client;

    public ProductClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    public SkuSnapshot sku(long skuId) {
        return client.get("/internal/products/skus/" + skuId, SkuSnapshot.class);
    }
}
