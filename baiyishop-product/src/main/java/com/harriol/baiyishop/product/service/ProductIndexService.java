package com.harriol.baiyishop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.product.dto.ProductIndexDoc;
import com.harriol.baiyishop.product.entity.Brand;
import com.harriol.baiyishop.product.entity.Category;
import com.harriol.baiyishop.product.entity.Product;
import com.harriol.baiyishop.product.mapper.BrandMapper;
import com.harriol.baiyishop.product.mapper.CategoryMapper;
import com.harriol.baiyishop.product.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 搜索索引文档的产出方（REQ-302）。
 * <p>search-service 不连商品库，只能通过内部接口取数据（架构 2.6：服务只能访问自己的存储）。
 * 单个文档用于增量同步，分页列表用于全量重建。
 * <p>删除（逻辑删除）的商品查不到 → 抛 30007，搜索侧据此从索引中移除该文档。
 */
@Service
public class ProductIndexService {

    /** 全量重建时单页上限，避免一次拉爆内存 */
    private static final long MAX_PAGE_SIZE = 500;

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;

    public ProductIndexService(ProductMapper productMapper, CategoryMapper categoryMapper, BrandMapper brandMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
    }

    /** 单个商品的索引文档；商品不存在或已删除时抛 30007 */
    public ProductIndexDoc indexDoc(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return toDocs(List.of(product)).get(0);
    }

    /** 全量索引文档（含已下架，下架由搜索侧过滤），按 id 升序保证翻页稳定 */
    public PageResult<ProductIndexDoc> indexDocPage(long page, long size) {
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Product> result = productMapper.selectPage(new Page<>(Math.max(page, 1), safeSize),
                Wrappers.<Product>lambdaQuery().orderByAsc(Product::getId));
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), toDocs(result.getRecords()));
    }

    /** 批量组装，分类与品牌各查一次，避免 N+1 */
    private List<ProductIndexDoc> toDocs(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, Category> categories = byId(categoryMapper.selectBatchIds(products.stream()
                .map(Product::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet())), Category::getId);
        Map<Long, Brand> brands = byId(brandMapper.selectBatchIds(products.stream()
                .map(Product::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet())), Brand::getId);

        return products.stream().map(product -> {
            Category category = product.getCategoryId() == null ? null : categories.get(product.getCategoryId());
            Brand brand = product.getBrandId() == null ? null : brands.get(product.getBrandId());
            return new ProductIndexDoc(product.getId(), product.getName(), product.getMainImage(),
                    product.getMinPrice(), product.getSales(),
                    product.getCategoryId(), category == null ? null : category.getPath(),
                    category == null ? null : category.getName(),
                    product.getBrandId(), brand == null ? null : brand.getName(),
                    product.getStatus(), product.getOnSaleTime(), product.getUpdatedAt());
        }).toList();
    }

    private <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idOf) {
        Map<Long, T> map = new HashMap<>();
        rows.forEach(row -> map.put(idOf.apply(row), row));
        return map;
    }
}
