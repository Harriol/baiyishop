package com.harriol.baiyishop.product.service;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.product.dto.ProductSkuSnapshot;
import com.harriol.baiyishop.product.entity.Product;
import com.harriol.baiyishop.product.entity.ProductSku;
import com.harriol.baiyishop.product.mapper.ProductMapper;
import com.harriol.baiyishop.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SKU / 商品快照读取（docs/api.md 第 6 章，供 order-service 使用）。
 * <p>批量接口按 skuId 去重后查两次（SKU 一次、商品一次），避免购物车里 N 个条目打 N 组请求。
 * <p>查不到（SKU 或商品被逻辑删除、停用）时**直接丢弃该条目**：调用方要能区分
 * 「查不到」与「查到但不可售」，前者是数据消失，后者是业务状态。
 */
@Service
public class ProductSnapshotService {

    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;

    public ProductSnapshotService(ProductSkuMapper skuMapper, ProductMapper productMapper) {
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
    }

    /** 单个 SKU 快照；不存在（含已删除）抛 30007 语义的商品不存在 */
    public ProductSkuSnapshot skuSnapshot(long skuId) {
        List<ProductSkuSnapshot> snapshots = skuSnapshots(List.of(skuId));
        if (snapshots.isEmpty()) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return snapshots.get(0);
    }

    /** 批量 SKU 快照；查不到的 SKU 不出现在结果里 */
    public List<ProductSkuSnapshot> skuSnapshots(Collection<Long> skuIds) {
        Set<Long> ids = skuIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ProductSku> skus = skuMapper.selectBatchIds(ids);
        if (skus.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = skus.stream().map(ProductSku::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = new HashMap<>();
        productMapper.selectBatchIds(productIds).forEach(product -> products.put(product.getId(), product));

        List<ProductSkuSnapshot> snapshots = new ArrayList<>();
        for (ProductSku sku : skus) {
            Product product = products.get(sku.getProductId());
            if (product == null) {
                // 商品被删除，SKU 已无意义，直接不返回
                continue;
            }
            snapshots.add(new ProductSkuSnapshot(sku.getId(), sku.getSkuCode(), sku.getSpecName(), sku.getPrice(),
                    sku.getImage() == null ? product.getMainImage() : sku.getImage(),
                    sku.getStatus() != null && sku.getStatus() == 1,
                    product.getId(), product.getName(), product.getMainImage(), product.getStatus()));
        }
        return snapshots;
    }

}
