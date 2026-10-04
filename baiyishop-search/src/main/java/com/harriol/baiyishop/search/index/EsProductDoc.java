package com.harriol.baiyishop.search.index;

import com.harriol.baiyishop.search.dto.IndexDocView;
import com.harriol.baiyishop.search.dto.SearchItem;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * ES 索引 {@code baiyishop_product} 的文档结构（REQ-301、docs/architecture.md 5.5）。
 * <p>两点值得说明：
 * <ul>
 *   <li><b>categoryIds</b>：把分类物化路径 {@code /1/12/135/} 展开成 id 数组，
 *       于是「按分类筛选含子分类」变成一次 {@code term} 命中祖先 —— 不必回查分类树
 *       （搜索服务没有分类库，也不该有）</li>
 *   <li><b>onSaleTime</b>：存毫秒时间戳而非日期字符串，避免时区在「写入 → 打分」之间漂移；
 *       新鲜度打分直接用 doc value 里的毫秒数与当前时间相减</li>
 * </ul>
 * <p>下架商品也在索引里（status = OFF_SALE），查询时统一过滤，
 * 这样商品重新上架可以立刻被搜到，不必等索引重建。
 */
public record EsProductDoc(
        Long id,
        String name,
        String mainImage,
        Long price,
        Integer sales,
        Long categoryId,
        List<Long> categoryIds,
        String categoryPath,
        String categoryName,
        Long brandId,
        String brandName,
        String status,
        Long onSaleTime) {

    public static final String STATUS_ON_SALE = "ON_SALE";

    public static EsProductDoc from(IndexDocView doc) {
        return new EsProductDoc(doc.id(), doc.name(), doc.mainImage(), doc.price(), doc.sales(),
                doc.categoryId(), categoryIdsOf(doc.categoryPath()), doc.categoryPath(), doc.categoryName(),
                doc.brandId(), doc.brandName(), doc.status(), toEpochMillis(doc.onSaleTime()));
    }

    /** 搜索结果项：只保留列表页要用的字段 */
    public SearchItem toItem() {
        return new SearchItem(id, name, mainImage, price, sales, categoryId, brandId, brandName);
    }

    /** "/1/12/135/" → [1, 12, 135]；路径异常时返回空列表而不是抛错，避免脏数据挡住索引 */
    private static List<Long> categoryIdsOf(String path) {
        List<Long> ids = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return ids;
        }
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                try {
                    ids.add(Long.parseLong(segment));
                } catch (NumberFormatException ignored) {
                    // 非数字段直接跳过：索引可用性优先于数据洁癖
                }
            }
        }
        return ids;
    }

    private static Long toEpochMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
