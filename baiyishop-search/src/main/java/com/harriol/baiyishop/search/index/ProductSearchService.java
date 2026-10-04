package com.harriol.baiyishop.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMapper;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.search.config.SearchProperties;
import com.harriol.baiyishop.search.dto.SearchItem;
import jakarta.json.stream.JsonParser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;

/**
 * 商品检索（REQ-301）。
 * <p>请求体走 JSON → {@link Query} 反序列化（可读性优先），响应走类型化对象（免去手写字段解析）。
 */
@Service
public class ProductSearchService {

    /** 单页上限：搜索页不需要一次拉太多，也避免深分页打爆 ES */
    private static final long MAX_PAGE_SIZE = 100;

    private final ElasticsearchClient client;
    private final JsonpMapper jsonpMapper;
    private final ProductSearchQueryBuilder queryBuilder;
    private final SearchProperties properties;

    public ProductSearchService(ElasticsearchClient client,
                                JsonpMapper jsonpMapper,
                                ProductSearchQueryBuilder queryBuilder,
                                SearchProperties properties) {
        this.client = client;
        this.jsonpMapper = jsonpMapper;
        this.queryBuilder = queryBuilder;
        this.properties = properties;
    }

    public PageResult<SearchItem> search(String keyword, Long categoryId, Long brandId,
                                         Long minPrice, Long maxPrice, String sort, long page, long size) {
        String sortKey = queryBuilder.sortKey(sort);
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Query query = parse(queryBuilder.build(keyword, categoryId, brandId, minPrice, maxPrice,
                queryBuilder.isComposite(sortKey)));
        SearchRequest request = SearchRequest.of(s -> s
                .index(properties.indexAlias())
                .from((int) ((safePage - 1) * safeSize))
                .size((int) safeSize)
                .trackTotalHits(t -> t.enabled(true))
                .query(query)
                .sort(sorts(sortKey)));

        SearchResponse<EsProductDoc> response;
        try {
            response = client.search(request, EsProductDoc.class);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "搜索服务暂时不可用，请稍后重试");
        }

        List<SearchItem> items = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(EsProductDoc::toItem)
                .toList();
        long total = response.hits().total() == null ? items.size() : response.hits().total().value();
        return PageResult.of(safePage, safeSize, total, items);
    }

    private List<SortOptions> sorts(String sortKey) {
        // 统一用 id 兜底做二级排序，避免同分值（同销量 / 同价格）时分页顺序漂移
        SortOptions idDesc = SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Desc)));
        return switch (sortKey) {
            case ProductSearchQueryBuilder.SORT_SALES -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("sales").order(SortOrder.Desc))), idDesc);
            case ProductSearchQueryBuilder.SORT_PRICE_ASC -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Asc))), idDesc);
            case ProductSearchQueryBuilder.SORT_PRICE_DESC -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Desc))), idDesc);
            // 综合：script_score 已把得分写进 _score，按 _score 降序即为综合得分降序
            default -> List.of(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))), idDesc);
        };
    }

    private Query parse(String json) {
        JsonParser parser = jsonpMapper.jsonProvider().createParser(new StringReader(json));
        try {
            return Query._DESERIALIZER.deserialize(parser, jsonpMapper);
        } catch (RuntimeException ex) {
            // 查询体由本服务生成，解析失败属于代码缺陷，直接暴露为 500 便于定位
            throw new IllegalStateException("搜索查询体解析失败：" + json, ex);
        }
    }
}
