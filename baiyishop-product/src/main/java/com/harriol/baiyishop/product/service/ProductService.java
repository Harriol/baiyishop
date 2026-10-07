package com.harriol.baiyishop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.product.dto.ProductAdminItem;
import com.harriol.baiyishop.product.dto.ProductChangedEvent;
import com.harriol.baiyishop.product.dto.ProductDetailResponse;
import com.harriol.baiyishop.product.dto.ProductListItem;
import com.harriol.baiyishop.product.dto.ProductParamRequest;
import com.harriol.baiyishop.product.dto.ProductParamResponse;
import com.harriol.baiyishop.product.dto.ProductPublicDetail;
import com.harriol.baiyishop.product.dto.ProductRequest;
import com.harriol.baiyishop.product.dto.ProductSkuRequest;
import com.harriol.baiyishop.product.dto.ProductSkuResponse;
import com.harriol.baiyishop.product.entity.Brand;
import com.harriol.baiyishop.product.entity.Category;
import com.harriol.baiyishop.product.entity.Product;
import com.harriol.baiyishop.product.entity.ParamItem;
import com.harriol.baiyishop.product.entity.ProductImage;
import com.harriol.baiyishop.product.entity.ProductParamValue;
import com.harriol.baiyishop.product.entity.ProductSku;
import com.harriol.baiyishop.product.mapper.BrandMapper;
import com.harriol.baiyishop.product.mapper.CategoryMapper;
import com.harriol.baiyishop.product.mapper.ParamItemMapper;
import com.harriol.baiyishop.product.mapper.ProductImageMapper;
import com.harriol.baiyishop.product.mapper.ProductParamValueMapper;
import com.harriol.baiyishop.product.mapper.ProductMapper;
import com.harriol.baiyishop.product.mapper.ProductSkuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品与 SKU 管理（REQ-203、REQ-206）。
 * <p>关键约定：
 * <ul>
 *   <li>商品挂在**叶子分类**上，避免分类树下钻时同一商品在多层重复出现</li>
 *   <li>sku_code 由服务端生成 {productId}-{两位序号}，不接受前端传入</li>
 *   <li>本服务**不存库存**：库存权威在 inventory-service（ADR-004、ADR-007）</li>
 *   <li>下架与删除分离：下架改 status，删除走逻辑删除</li>
 *   <li>新增 / 修改 / 上下架 / 删除都在**同一事务内**写一条 mq_outbox，
 *       由投递任务异步同步到搜索索引（ADR-005），商品保存不依赖 ES 可用性</li>
 * </ul>
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductImageMapper imageMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final CategoryService categoryService;
    private final ParamItemMapper paramItemMapper;
    private final ProductParamValueMapper paramValueMapper;
    private final OutboxService outboxService;

    public ProductService(ProductMapper productMapper,
                          ProductSkuMapper skuMapper,
                          ProductImageMapper imageMapper,
                          CategoryMapper categoryMapper,
                          BrandMapper brandMapper,
                          CategoryService categoryService,
                          ParamItemMapper paramItemMapper,
                          ProductParamValueMapper paramValueMapper,
                          OutboxService outboxService) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.imageMapper = imageMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.categoryService = categoryService;
        this.paramItemMapper = paramItemMapper;
        this.paramValueMapper = paramValueMapper;
        this.outboxService = outboxService;
    }

    /**
     * 同步商品参数值（REQ-204）。
     * <p>三条规则：
     * <ul>
     *   <li>参数项必须存在，否则 30011</li>
     *   <li>同一商品的参数项必须来自**同一个模板**，否则 30009（后台统一维护参数口径）</li>
     *   <li>product_param_value 没有 deleted 字段，是物理删除，可安全地「先清后插」</li>
     * </ul>
     */
    private void syncParams(Product product, List<ProductParamRequest> params) {
        paramValueMapper.delete(Wrappers.<ProductParamValue>lambdaQuery()
                .eq(ProductParamValue::getProductId, product.getId()));
        if (CollectionUtils.isEmpty(params)) {
            return;
        }
        List<Long> itemIds = params.stream().map(ProductParamRequest::paramItemId).distinct().toList();
        List<ParamItem> items = paramItemMapper.selectBatchIds(itemIds);
        if (items.size() != itemIds.size()) {
            throw new BizException(ErrorCode.PARAM_ITEM_NOT_FOUND);
        }
        long templateCount = items.stream().map(ParamItem::getTemplateId).distinct().count();
        if (templateCount > 1) {
            throw new BizException(ErrorCode.PARAM_ITEM_TEMPLATE_MISMATCH);
        }
        for (ProductParamRequest request : params) {
            ParamItem item = items.stream()
                    .filter(i -> i.getId().equals(request.paramItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.PARAM_ITEM_NOT_FOUND));
            ProductParamValue value = new ProductParamValue();
            value.setProductId(product.getId());
            value.setParamItemId(item.getId());
            value.setValue(request.value().trim());
            value.setSort(item.getSort());
            paramValueMapper.insert(value);
        }
    }

    /** 读取商品参数，按参数项排序；参数项被删除时该条自动不再展示 */
    private List<ProductParamResponse> paramsOf(Long productId) {
        List<ProductParamValue> values = paramValueMapper.selectList(Wrappers.<ProductParamValue>lambdaQuery()
                .eq(ProductParamValue::getProductId, productId)
                .orderByAsc(ProductParamValue::getSort)
                .orderByAsc(ProductParamValue::getId));
        if (values.isEmpty()) {
            return List.of();
        }
        Map<Long, ParamItem> items = paramItemMapper
                .selectBatchIds(values.stream().map(ProductParamValue::getParamItemId).distinct().toList())
                .stream().collect(Collectors.toMap(ParamItem::getId, item -> item));
        return values.stream()
                .filter(value -> items.containsKey(value.getParamItemId()))
                .map(value -> new ProductParamResponse(items.get(value.getParamItemId()).getName(), value.getValue()))
                .toList();
    }

    /**
     * 前台分类商品列表（REQ-205）。
     * <p>按分类**含子分类**查询，只返回上架商品；分类下没有商品时返回空列表而不是报错，
     * 由前端展示空态。
     *
     * @param sort sales 销量 / new 上新 / price_asc / price_desc，默认按销量
     */
    public PageResult<ProductListItem> publicPage(Long categoryId, String sort, long page, long size) {
        List<Long> categoryIds = categoryService.selfAndDescendantIds(categoryId);

        Page<Product> pager = new Page<>(page, size);
        var query = Wrappers.<Product>lambdaQuery()
                .in(Product::getCategoryId, categoryIds)
                .eq(Product::getStatus, Product.STATUS_ON_SALE);
        switch (sort == null ? "sales" : sort) {
            case "new" -> query.orderByDesc(Product::getOnSaleTime).orderByDesc(Product::getId);
            case "price_asc" -> query.orderByAsc(Product::getMinPrice).orderByDesc(Product::getId);
            case "price_desc" -> query.orderByDesc(Product::getMinPrice).orderByDesc(Product::getId);
            default -> query.orderByDesc(Product::getSales).orderByDesc(Product::getId);
        }
        Page<Product> result = productMapper.selectPage(pager, query);

        Map<Long, String> brandNames = namesOfBrands(result.getRecords().stream()
                .map(Product::getBrandId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        List<ProductListItem> items = result.getRecords().stream()
                .map(p -> new ProductListItem(p.getId(), p.getName(), p.getMainImage(), p.getMinPrice(),
                        p.getSales(), p.getCategoryId(), p.getBrandId(),
                        p.getBrandId() == null ? null : brandNames.get(p.getBrandId())))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    /**
     * 前台商品详情（REQ-206）。
     * <p>已下架商品也返回 200 与 status=OFF_SALE，由前端给提示 —— 直接报错会让用户以为系统坏了。
     * <p>库存字段恒为 null：本服务不存库存，待 inventory-service 就绪后只读接入。
     */
    public ProductPublicDetail publicDetail(Long id) {
        Product product = require(id);
        List<String> images = imageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                        .eq(ProductImage::getProductId, id).orderByAsc(ProductImage::getSort))
                .stream().map(ProductImage::getUrl).toList();
        List<ProductSkuResponse> skus = skusOf(id).stream()
                .filter(sku -> sku.getStatus() != null && sku.getStatus() == 1)
                .map(ProductSkuResponse::from).toList();

        Category category = categoryMapper.selectById(product.getCategoryId());
        Brand brand = product.getBrandId() == null ? null : brandMapper.selectById(product.getBrandId());
        return new ProductPublicDetail(product.getId(), product.getName(), product.getCategoryId(),
                category == null ? null : category.getName(), category == null ? null : category.getPath(),
                product.getBrandId(), brand == null ? null : brand.getName(), product.getMainImage(),
                images, product.getDetail(), product.getStatus(), product.getMinPrice(), product.getSales(),
                null, skus, paramsOf(id));
    }

    /** 后台商品分页（关键词 / 分类 / 品牌 / 状态） */
    public PageResult<ProductAdminItem> page(long page, long size, String keyword,
                                             Long categoryId, Long brandId, String status) {
        // 分类筛选按「含子分类」语义：后台选一级分类时也要能看到其下叶子分类里的商品
        List<Long> categoryIds = categoryId == null ? null : categoryService.selfAndDescendantIds(categoryId);
        Page<Product> pager = new Page<>(page, size);
        Page<Product> result = productMapper.selectPage(pager, Wrappers.<Product>lambdaQuery()
                .like(StringUtils.hasText(keyword), Product::getName, keyword)
                .in(categoryIds != null, Product::getCategoryId, categoryIds == null ? List.of() : categoryIds)
                .eq(brandId != null, Product::getBrandId, brandId)
                .eq(StringUtils.hasText(status), Product::getStatus, status)
                .orderByDesc(Product::getId));

        List<Product> records = result.getRecords();
        Map<Long, String> categoryNames = namesOfCategories(records.stream().map(Product::getCategoryId).collect(Collectors.toSet()));
        Map<Long, String> brandNames = namesOfBrands(records.stream().map(Product::getBrandId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));

        List<ProductAdminItem> items = records.stream()
                .map(p -> new ProductAdminItem(p.getId(), p.getName(), p.getMainImage(),
                        p.getCategoryId(), categoryNames.get(p.getCategoryId()),
                        p.getBrandId(), p.getBrandId() == null ? null : brandNames.get(p.getBrandId()),
                        p.getMinPrice(), p.getSales(), p.getStatus(), p.getOnSaleTime()))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    public ProductDetailResponse detail(Long id) {
        Product product = require(id);
        List<String> images = imageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                        .eq(ProductImage::getProductId, id).orderByAsc(ProductImage::getSort))
                .stream().map(ProductImage::getUrl).toList();
        List<ProductSkuResponse> skus = skusOf(id).stream().map(ProductSkuResponse::from).toList();

        Category category = categoryMapper.selectById(product.getCategoryId());
        Brand brand = product.getBrandId() == null ? null : brandMapper.selectById(product.getBrandId());
        return new ProductDetailResponse(product.getId(), product.getName(), product.getCategoryId(),
                category == null ? null : category.getName(), product.getBrandId(),
                brand == null ? null : brand.getName(), product.getMainImage(), images, product.getDetail(),
                product.getStatus(), product.getMinPrice(), product.getSales(), product.getOnSaleTime(), skus,
                paramsOf(id));
    }

    @Transactional
    public ProductDetailResponse create(ProductRequest request) {
        Category category = requireLeafCategory(request.categoryId());
        Brand brand = request.brandId() == null ? null : requireBrand(request.brandId());

        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setBrandId(brand == null ? null : brand.getId());
        product.setSales(0);
        apply(product, request);
        productMapper.insert(product);

        // 自增 ID 生成后才能算 SKU 编码
        syncSkus(product, request.skus());
        saveImages(product.getId(), request);
        syncParams(product, request.params());
        syncMinPriceAndStatus(product, request);
        outboxService.appendProductChanged(product.getId(), ProductChangedEvent.ACTION_UPSERT);
        log.info("新增商品 id={} name={} skus={}", product.getId(), product.getName(), request.skus().size());
        return detail(product.getId());
    }

    @Transactional
    public ProductDetailResponse update(Long id, ProductRequest request) {
        Product product = require(id);
        Category category = requireLeafCategory(request.categoryId());
        Brand brand = request.brandId() == null ? null : requireBrand(request.brandId());

        product.setCategoryId(category.getId());
        product.setBrandId(brand == null ? null : brand.getId());
        apply(product, request);

        // 图集表没有逻辑删除，可直接物理替换
        imageMapper.delete(Wrappers.<ProductImage>lambdaQuery().eq(ProductImage::getProductId, id));
        // SKU 表是逻辑删除：若「删了再插」会撞 uk_sku_code，故按下标原地更新，多余的才逻辑删除
        syncSkus(product, request.skus());
        saveImages(id, request);
        syncParams(product, request.params());
        syncMinPriceAndStatus(product, request);
        outboxService.appendProductChanged(id, ProductChangedEvent.ACTION_UPSERT);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        Product product = require(id);
        skuMapper.delete(Wrappers.<ProductSku>lambdaQuery().eq(ProductSku::getProductId, id));
        imageMapper.delete(Wrappers.<ProductImage>lambdaQuery().eq(ProductImage::getProductId, id));
        productMapper.deleteById(product.getId());
        outboxService.appendProductChanged(id, ProductChangedEvent.ACTION_DELETE);
        log.info("删除商品（逻辑删除）id={}", id);
    }

    @Transactional
    public ProductDetailResponse changeStatus(Long id, boolean onSale) {
        Product product = require(id);
        product.setStatus(onSale ? Product.STATUS_ON_SALE : Product.STATUS_OFF_SALE);
        product.setOnSaleTime(onSale ? LocalDateTime.now() : product.getOnSaleTime());
        productMapper.updateById(product);
        outboxService.appendProductChanged(id, ProductChangedEvent.ACTION_UPSERT);
        return detail(id);
    }

    /** 商品归属的叶子分类校验 */
    private Category requireLeafCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        long children = categoryMapper.countDescendants(category.getPath(), category.getId());
        if (children > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品只能挂在叶子分类上，当前分类下还有子分类");
        }
        return category;
    }

    private Brand requireBrand(Long brandId) {
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(ErrorCode.BRAND_NOT_FOUND);
        }
        return brand;
    }

    private Product require(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setMainImage(request.mainImage());
        product.setDetail(request.detail());
        if (request.onSale() != null) {
            product.setStatus(request.onSale() ? Product.STATUS_ON_SALE : Product.STATUS_OFF_SALE);
            if (Boolean.TRUE.equals(request.onSale())) {
                product.setOnSaleTime(LocalDateTime.now());
            }
        } else if (product.getStatus() == null) {
            product.setStatus(Product.STATUS_OFF_SALE);
        }
    }

    /**
     * 同步 SKU：按下标原地更新，编码保持 {productId}-{两位序号}，多余的逻辑删除。
     * <p>不能「先全删再插」——product_sku 是逻辑删除，旧行的 sku_code 仍在，
     * 重新插入相同编码会撞 uk_sku_code。
     */
    private void syncSkus(Product product, List<ProductSkuRequest> requests) {
        List<ProductSku> existing = skusOf(product.getId());
        for (int i = 0; i < requests.size(); i++) {
            ProductSkuRequest request = requests.get(i);
            boolean isNew = i >= existing.size();
            ProductSku sku = isNew ? new ProductSku() : existing.get(i);
            if (isNew) {
                sku.setProductId(product.getId());
                sku.setSkuCode(product.getId() + "-" + String.format("%02d", i + 1));
            }
            sku.setSpecName(StringUtils.hasText(request.specName()) ? request.specName().trim() : "默认规格");
            sku.setPrice(request.price());
            sku.setImage(request.image());
            sku.setSort(request.sort() == null ? 0 : request.sort());
            sku.setStatus(request.enabled() == null || request.enabled() ? 1 : 0);
            if (isNew) {
                skuMapper.insert(sku);
            } else {
                skuMapper.updateById(sku);
            }
        }
        for (int i = requests.size(); i < existing.size(); i++) {
            skuMapper.deleteById(existing.get(i).getId());
        }
    }

    private void saveImages(Long productId, ProductRequest request) {
        List<String> urls = request.images();
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }
        int sort = 0;
        for (String url : urls) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setUrl(url);
            image.setSort(sort++);
            imageMapper.insert(image);
        }
    }

    /** 冗余字段与状态收口：min_price 取最低价 SKU，sales 初始化为 0 */
    private void syncMinPriceAndStatus(Product product, ProductRequest request) {
        long minPrice = request.skus().stream()
                .map(ProductSkuRequest::price)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(0L);
        product.setMinPrice(minPrice);
        if (product.getSales() == null) {
            product.setSales(0);
        }
        productMapper.updateById(product);
    }

    private List<ProductSku> skusOf(Long productId) {
        return skuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getSort)
                .orderByAsc(ProductSku::getId));
    }

    private Map<Long, String> namesOfCategories(Set<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        categoryMapper.selectBatchIds(ids).forEach(c -> map.put(c.getId(), c.getName()));
        return map;
    }

    private Map<Long, String> namesOfBrands(Set<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        brandMapper.selectBatchIds(new ArrayList<>(ids)).forEach(b -> map.put(b.getId(), b.getName()));
        return map;
    }
}
