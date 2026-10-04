package com.harriol.baiyishop.search.index;

import com.harriol.baiyishop.search.config.SearchProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 组装 ES 查询体（REQ-301）。
 * <p>查询体用 JSON 拼装而不是 Java DSL：综合排序需要 {@code script_score}，
 * 写成 JSON 与 ES 官方文档逐字对应，排查线上问题时可以直接贴进 Kibana 验证。
 * <p>所有拼接进 JSON 的数值都来自后端解析出的 Long，唯一的字符串（关键词）
 * 走 Jackson 转义，不存在注入问题。
 */
@Component
public class ProductSearchQueryBuilder {

    public static final String SORT_COMPOSITE = "composite";
    public static final String SORT_SALES = "sales";
    public static final String SORT_PRICE_ASC = "price_asc";
    public static final String SORT_PRICE_DESC = "price_desc";

    /** 综合得分脚本：销量取对数并归一化，上架时间换算成 0~1 的新鲜度 */
    private static final String COMPOSITE_SCRIPT = """
            double salesScore = 0.0;
            if (doc['sales'].size() > 0) {
              salesScore = Math.log(1.0 + doc['sales'].value) / params.salesLogMax;
              if (salesScore > 1.0) { salesScore = 1.0; }
            }
            double freshness = 0.0;
            if (doc['onSaleTime'].size() > 0) {
              double ageDays = (params.now - doc['onSaleTime'].value.toInstant().toEpochMilli()) / 86400000.0;
              freshness = 1.0 - ageDays / params.freshnessDays;
              if (freshness < 0.0) { freshness = 0.0; }
              if (freshness > 1.0) { freshness = 1.0; }
            }
            return params.salesWeight * salesScore + params.freshnessWeight * freshness;
            """;

    private final ObjectMapper objectMapper;
    private final SearchProperties properties;

    public ProductSearchQueryBuilder(ObjectMapper objectMapper, SearchProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String sortKey(String sort) {
        return switch (sort == null ? "" : sort) {
            case SORT_SALES, SORT_PRICE_ASC, SORT_PRICE_DESC -> sort;
            // 未知值按默认综合排序，前端传错也不至于报错
            default -> SORT_COMPOSITE;
        };
    }

    public boolean isComposite(String sortKey) {
        return SORT_COMPOSITE.equals(sortKey);
    }

    public String build(String keyword, Long categoryId, Long brandId, Long minPrice, Long maxPrice, boolean composite) {
        StringBuilder filters = new StringBuilder();
        filters.append("{\"term\":{\"status\":\"").append(EsProductDoc.STATUS_ON_SALE).append("\"}}");
        if (categoryId != null) {
            // categoryIds 是分类物化路径展开的 id 数组，命中等价于「含子分类」
            filters.append(",{\"term\":{\"categoryIds\":").append(categoryId).append("}}");
        }
        if (brandId != null) {
            filters.append(",{\"term\":{\"brandId\":").append(brandId).append("}}");
        }
        if (minPrice != null || maxPrice != null) {
            filters.append(",{\"range\":{\"price\":{");
            if (minPrice != null) {
                filters.append("\"gte\":").append(minPrice);
            }
            if (maxPrice != null) {
                filters.append(minPrice != null ? "," : "").append("\"lte\":").append(maxPrice);
            }
            filters.append("}}}");
        }

        StringBuilder bool = new StringBuilder("{\"bool\":{\"filter\":[").append(filters).append("]");
        if (keyword != null && !keyword.isBlank()) {
            // 分词后要求全部命中，保证「纯棉T恤」不会把纯棉的其它品类也带出来
            bool.append(",\"must\":[{\"match\":{\"name\":{\"query\":")
                    .append(stringLiteral(keyword.trim()))
                    .append(",\"operator\":\"and\"}}}]");
        }
        bool.append("}}");

        if (!composite) {
            return bool.toString();
        }
        long now = System.currentTimeMillis();
        return "{\"script_score\":{\"query\":" + bool
                + ",\"script\":{\"lang\":\"painless\",\"source\":" + stringLiteral(COMPOSITE_SCRIPT)
                + ",\"params\":{\"salesWeight\":" + properties.salesWeight()
                + ",\"freshnessWeight\":" + properties.freshnessWeight()
                + ",\"salesLogMax\":" + Math.log(1.0 + properties.salesNormalizeMax())
                + ",\"freshnessDays\":" + properties.freshnessDays()
                + ",\"now\":" + now + "}}}}";
    }

    /** 交给 Jackson 生成 JSON 字符串字面量，避免手写转义出错 */
    private String stringLiteral(String raw) {
        return objectMapper.writeValueAsString(raw);
    }
}
