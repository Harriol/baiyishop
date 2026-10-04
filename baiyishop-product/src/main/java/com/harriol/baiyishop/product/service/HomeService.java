package com.harriol.baiyishop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.product.dto.ProductListItem;
import com.harriol.baiyishop.product.dto.home.BannerItem;
import com.harriol.baiyishop.product.dto.home.FloorItem;
import com.harriol.baiyishop.product.dto.home.HomeFloorView;
import com.harriol.baiyishop.product.dto.home.HomeResponse;
import com.harriol.baiyishop.product.dto.home.NavItem;
import com.harriol.baiyishop.product.dto.home.NoticeItem;
import com.harriol.baiyishop.product.entity.Brand;
import com.harriol.baiyishop.product.entity.HomeBanner;
import com.harriol.baiyishop.product.entity.HomeFloor;
import com.harriol.baiyishop.product.entity.HomeFloorItem;
import com.harriol.baiyishop.product.entity.HomeNav;
import com.harriol.baiyishop.product.entity.HomeNotice;
import com.harriol.baiyishop.product.entity.Product;
import com.harriol.baiyishop.product.mapper.BrandMapper;
import com.harriol.baiyishop.product.mapper.HomeBannerMapper;
import com.harriol.baiyishop.product.mapper.HomeFloorItemMapper;
import com.harriol.baiyishop.product.mapper.HomeFloorMapper;
import com.harriol.baiyishop.product.mapper.HomeNavMapper;
import com.harriol.baiyishop.product.mapper.HomeNoticeMapper;
import com.harriol.baiyishop.product.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 首页配置（REQ-401 ~ REQ-403）。
 * <p>四类配置统一采用「整表替换」语义（GET 读全量 / PUT 存全量），与本项目其它接口保持一致。
 * <p>这些表没有 deleted 字段，替换时是物理删除，不会像逻辑删除那样留下占位行。
 * <p>配置量小且未加缓存，因此**保存后前台刷新即生效**；后续若按架构 7.1 引入
 * `baiyishop:home:config` 缓存，需要在保存后主动失效。
 */
@Service
public class HomeService {

    private static final Logger log = LoggerFactory.getLogger(HomeService.class);

    private static final int DEFAULT_FLOOR_LIMIT = 8;

    private final HomeBannerMapper bannerMapper;
    private final HomeNoticeMapper noticeMapper;
    private final HomeNavMapper navMapper;
    private final HomeFloorMapper floorMapper;
    private final HomeFloorItemMapper floorItemMapper;
    private final ProductMapper productMapper;
    private final CategoryService categoryService;
    private final BrandMapper brandMapper;
    public HomeService(HomeBannerMapper bannerMapper,
                       HomeNoticeMapper noticeMapper,
                       HomeNavMapper navMapper,
                       HomeFloorMapper floorMapper,
                       HomeFloorItemMapper floorItemMapper,
                       ProductMapper productMapper,
                       CategoryService categoryService,
                       BrandMapper brandMapper) {
        this.bannerMapper = bannerMapper;
        this.noticeMapper = noticeMapper;
        this.navMapper = navMapper;
        this.floorMapper = floorMapper;
        this.floorItemMapper = floorItemMapper;
        this.productMapper = productMapper;
        this.categoryService = categoryService;
        this.brandMapper = brandMapper;
    }

    /**
     * 整表替换前的清理。
     * <p>不能直接 `delete(空条件)` —— 那会触发 common-data 里配置的 BlockAttackInnerInterceptor
     * （拦截无 where 的 update / delete，ADR-006 的红线）。这里按刚读到的 id 批量删除，
     * 生成的语句带 where，既通过拦截器，也让「删除范围」显式可见。
     */
    private void deleteExisting(List<Long> ids, java.util.function.Consumer<List<Long>> deleter) {
        if (!ids.isEmpty()) {
            deleter.accept(ids);
        }
    }

    // ---------------- 后台读取 ----------------

    public List<BannerItem> banners() {
        return bannerMapper.selectList(Wrappers.<HomeBanner>lambdaQuery()
                        .orderByAsc(HomeBanner::getSort).orderByAsc(HomeBanner::getId))
                .stream()
                .map(b -> new BannerItem(b.getTitle(), b.getImageUrl(), b.getLinkType(), b.getLinkValue(),
                        b.getSort(), b.getEnabled() != null && b.getEnabled() == 1))
                .toList();
    }

    public List<NoticeItem> notices() {
        return noticeMapper.selectList(Wrappers.<HomeNotice>lambdaQuery()
                        .orderByAsc(HomeNotice::getSort).orderByAsc(HomeNotice::getId))
                .stream()
                .map(n -> new NoticeItem(n.getContent(), n.getSort(), n.getEnabled() != null && n.getEnabled() == 1))
                .toList();
    }

    public List<NavItem> navs() {
        return navMapper.selectList(Wrappers.<HomeNav>lambdaQuery()
                        .orderByAsc(HomeNav::getSort).orderByAsc(HomeNav::getId))
                .stream()
                .map(n -> new NavItem(n.getName(), n.getIcon(), n.getCategoryId(), n.getSort(),
                        n.getEnabled() != null && n.getEnabled() == 1))
                .toList();
    }

    public List<FloorItem> floors() {
        return floorMapper.selectList(Wrappers.<HomeFloor>lambdaQuery()
                        .orderByAsc(HomeFloor::getSort).orderByAsc(HomeFloor::getId))
                .stream()
                .map(f -> new FloorItem(f.getTitle(), f.getCategoryId(), sortFieldToText(f.getSortField()),
                        f.getLimitSize(), f.getSort(), f.getEnabled() != null && f.getEnabled() == 1,
                        pinnedIdsOf(f.getId())))
                .toList();
    }

    // ---------------- 后台保存（整表替换） ----------------

    @Transactional
    public List<BannerItem> saveBanners(List<BannerItem> items) {
        deleteExisting(bannerMapper.selectList(Wrappers.<HomeBanner>lambdaQuery().select(HomeBanner::getId)).stream().map(HomeBanner::getId).toList(), bannerMapper::deleteByIds);
        int index = 0;
        for (BannerItem item : items) {
            HomeBanner banner = new HomeBanner();
            banner.setTitle(item.title());
            banner.setImageUrl(item.imageUrl());
            banner.setLinkType(item.linkType() == null ? 0 : item.linkType());
            banner.setLinkValue(item.linkValue());
            banner.setSort(item.sort() == null ? index : item.sort());
            banner.setEnabled(item.enabled() == null || item.enabled() ? 1 : 0);
            bannerMapper.insert(banner);
            index++;
        }
        log.info("保存首页轮播 {} 条", items.size());
        return banners();
    }

    @Transactional
    public List<NoticeItem> saveNotices(List<NoticeItem> items) {
        deleteExisting(noticeMapper.selectList(Wrappers.<HomeNotice>lambdaQuery().select(HomeNotice::getId)).stream().map(HomeNotice::getId).toList(), noticeMapper::deleteByIds);
        int index = 0;
        for (NoticeItem item : items) {
            HomeNotice notice = new HomeNotice();
            notice.setContent(item.content());
            notice.setSort(item.sort() == null ? index : item.sort());
            notice.setEnabled(item.enabled() == null || item.enabled() ? 1 : 0);
            noticeMapper.insert(notice);
            index++;
        }
        return notices();
    }

    @Transactional
    public List<NavItem> saveNavs(List<NavItem> items) {
        deleteExisting(navMapper.selectList(Wrappers.<HomeNav>lambdaQuery().select(HomeNav::getId)).stream().map(HomeNav::getId).toList(), navMapper::deleteByIds);
        int index = 0;
        for (NavItem item : items) {
            if (item.categoryId() != null && !categoryService.exists(item.categoryId())) {
                throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
            }
            HomeNav nav = new HomeNav();
            nav.setName(item.name());
            nav.setIcon(item.icon());
            nav.setCategoryId(item.categoryId());
            nav.setSort(item.sort() == null ? index : item.sort());
            nav.setEnabled(item.enabled() == null || item.enabled() ? 1 : 0);
            navMapper.insert(nav);
            index++;
        }
        return navs();
    }

    @Transactional
    public List<FloorItem> saveFloors(List<FloorItem> items) {
        deleteExisting(floorMapper.selectList(Wrappers.<HomeFloor>lambdaQuery().select(HomeFloor::getId)).stream().map(HomeFloor::getId).toList(), floorMapper::deleteByIds);
        deleteExisting(floorItemMapper.selectList(Wrappers.<HomeFloorItem>lambdaQuery().select(HomeFloorItem::getId)).stream().map(HomeFloorItem::getId).toList(), floorItemMapper::deleteByIds);
        int index = 0;
        for (FloorItem item : items) {
            if (!categoryService.exists(item.categoryId())) {
                throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
            }
            List<Long> pinned = item.pinnedProductIds() == null ? List.of() : item.pinnedProductIds();
            if (!pinned.isEmpty() && productMapper.selectBatchIds(pinned).size() != new HashSet<>(pinned).size()) {
                throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            HomeFloor floor = new HomeFloor();
            floor.setTitle(item.title());
            floor.setCategoryId(item.categoryId());
            floor.setSortField(textToSortField(item.sortField()));
            floor.setLimitSize(item.limitSize() == null ? DEFAULT_FLOOR_LIMIT : item.limitSize());
            floor.setSort(item.sort() == null ? index : item.sort());
            floor.setEnabled(item.enabled() == null || item.enabled() ? 1 : 0);
            floorMapper.insert(floor);

            int pinnedIndex = 0;
            for (Long productId : pinned) {
                HomeFloorItem floorItem = new HomeFloorItem();
                floorItem.setFloorId(floor.getId());
                floorItem.setProductId(productId);
                floorItem.setSort(pinnedIndex++);
                floorItemMapper.insert(floorItem);
            }
            index++;
        }
        log.info("保存首页楼层 {} 个", items.size());
        return floors();
    }

    // ---------------- 前台聚合 ----------------

    /**
     * 前台首页聚合（REQ-403）。任一模块为空都返回空数组，双端做空态兜底，不报错。
     */
    public HomeResponse home() {
        List<HomeFloorView> floorViews = floorMapper.selectList(Wrappers.<HomeFloor>lambdaQuery()
                        .eq(HomeFloor::getEnabled, 1)
                        .orderByAsc(HomeFloor::getSort).orderByAsc(HomeFloor::getId))
                .stream()
                .map(floor -> new HomeFloorView(floor.getId(), floor.getTitle(), floor.getCategoryId(),
                        sortFieldToText(floor.getSortField()), productsOf(floor)))
                .toList();

        return new HomeResponse(
                banners().stream().filter(BannerItem::enabled).toList(),
                notices().stream().filter(NoticeItem::enabled).toList(),
                navs().stream().filter(NavItem::enabled).toList(),
                floorViews);
    }

    /**
     * 楼层商品：人工置顶在前，其余按该楼层自己的排序维度自动补齐（REQ-402）。
     * <p>只取上架商品；分类下没有商品时返回空列表，楼层照常渲染。
     */
    private List<ProductListItem> productsOf(HomeFloor floor) {
        int limit = floor.getLimitSize() == null ? DEFAULT_FLOOR_LIMIT : floor.getLimitSize();
        Map<Long, Product> picked = new LinkedHashMap<>();

        List<HomeFloorItem> pinned = floorItemMapper.selectList(Wrappers.<HomeFloorItem>lambdaQuery()
                .eq(HomeFloorItem::getFloorId, floor.getId())
                .orderByAsc(HomeFloorItem::getSort));
        if (!pinned.isEmpty()) {
            Map<Long, Product> pinnedProducts = productMapper
                    .selectBatchIds(pinned.stream().map(HomeFloorItem::getProductId).toList())
                    .stream()
                    .filter(p -> Product.STATUS_ON_SALE.equals(p.getStatus()))
                    .collect(Collectors.toMap(Product::getId, p -> p));
            for (HomeFloorItem item : pinned) {
                Product product = pinnedProducts.get(item.getProductId());
                if (product != null && picked.size() < limit) {
                    picked.put(product.getId(), product);
                }
            }
        }

        if (picked.size() < limit) {
            Set<Long> excluded = new HashSet<>(picked.keySet());
            var query = Wrappers.<Product>lambdaQuery()
                    .in(Product::getCategoryId, categoryService.selfAndDescendantIds(floor.getCategoryId()))
                    .eq(Product::getStatus, Product.STATUS_ON_SALE)
                    .notIn(!excluded.isEmpty(), Product::getId, excluded);
            applyFloorSort(query, floor.getSortField());
            // 只取需要的条数，不查总数
            Page<Product> page = new Page<>(1, limit - picked.size(), false);
            productMapper.selectPage(page, query).getRecords()
                    .forEach(product -> picked.put(product.getId(), product));
        }

        Set<Long> brandIds = picked.values().stream().map(Product::getBrandId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> brandNames = new LinkedHashMap<>();
        if (!brandIds.isEmpty()) {
            brandMapper.selectBatchIds(new ArrayList<>(brandIds))
                    .forEach((Brand brand) -> brandNames.put(brand.getId(), brand.getName()));
        }
        List<ProductListItem> result = new ArrayList<>();
        for (Product product : picked.values()) {
            result.add(new ProductListItem(product.getId(), product.getName(), product.getMainImage(),
                    product.getMinPrice(), product.getSales(), product.getCategoryId(), product.getBrandId(),
                    product.getBrandId() == null ? null : brandNames.get(product.getBrandId())));
        }
        return result;
    }

    private void applyFloorSort(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product> query, Integer sortField) {
        int field = sortField == null ? HomeFloor.SORT_SALES : sortField;
        switch (field) {
            case HomeFloor.SORT_NEW -> query.orderByDesc(Product::getOnSaleTime).orderByDesc(Product::getId);
            case HomeFloor.SORT_PRICE_ASC -> query.orderByAsc(Product::getMinPrice).orderByDesc(Product::getId);
            case HomeFloor.SORT_PRICE_DESC -> query.orderByDesc(Product::getMinPrice).orderByDesc(Product::getId);
            default -> query.orderByDesc(Product::getSales).orderByDesc(Product::getId);
        }
    }

    private List<Long> pinnedIdsOf(Long floorId) {
        return floorItemMapper.selectList(Wrappers.<HomeFloorItem>lambdaQuery()
                        .eq(HomeFloorItem::getFloorId, floorId)
                        .orderByAsc(HomeFloorItem::getSort))
                .stream().map(HomeFloorItem::getProductId).toList();
    }

    /** 对外用字符串，落库用 TINYINT（docs/database.md 4.12） */
    private String sortFieldToText(Integer sortField) {
        int field = sortField == null ? HomeFloor.SORT_SALES : sortField;
        return switch (field) {
            case HomeFloor.SORT_NEW -> "new";
            case HomeFloor.SORT_PRICE_ASC -> "price_asc";
            case HomeFloor.SORT_PRICE_DESC -> "price_desc";
            default -> "sales";
        };
    }

    private int textToSortField(String text) {
        if (text == null) {
            return HomeFloor.SORT_SALES;
        }
        return switch (text) {
            case "new" -> HomeFloor.SORT_NEW;
            case "price_asc" -> HomeFloor.SORT_PRICE_ASC;
            case "price_desc" -> HomeFloor.SORT_PRICE_DESC;
            default -> HomeFloor.SORT_SALES;
        };
    }
}