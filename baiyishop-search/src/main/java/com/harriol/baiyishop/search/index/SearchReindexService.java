package com.harriol.baiyishop.search.index;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.search.dto.IndexDocView;
import com.harriol.baiyishop.search.dto.ReindexResult;
import com.harriol.baiyishop.search.source.ProductIndexSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 全量重建索引（REQ-302、docs/api.md 第 6 章）。
 * <p>流程：建临时索引 → 分页从 product-service 拉全量 → bulk 写入 → 原子切换别名 → 删除旧索引。
 * 全程查询都走别名，因此**重建期间搜索不中断**，切完之后旧索引才下线。
 */
@Service
public class SearchReindexService {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexService.class);

    private static final long MAX_PAGE_SIZE = 500;
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SearchIndexService indexService;
    private final ProductIndexSource source;

    public SearchReindexService(SearchIndexService indexService, ProductIndexSource source) {
        this.indexService = indexService;
        this.source = source;
    }

    public ReindexResult reindex(int pageSize) {
        int safePageSize = (int) Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long start = System.currentTimeMillis();

        List<String> previous = indexService.indicesOfAlias();
        // 后缀带随机段：同一秒内连续两次重建也不能撞名（撞名会让「切换别名」退化为原地重建）
        String physical = indexService.alias() + "_" + LocalDateTime.now().format(SUFFIX)
                + "_" + UUID.randomUUID().toString().substring(0, 4);
        indexService.createIndex(physical, null);

        long indexed = 0;
        int page = 1;
        while (true) {
            PageResult<IndexDocView> slice = source.fetchPage(page, safePageSize);
            if (slice.getList().isEmpty()) {
                break;
            }
            indexed += indexService.bulkIndex(physical, slice.getList());
            if (slice.getList().size() < safePageSize) {
                break;
            }
            page++;
        }

        indexService.refresh(physical);
        indexService.swapAlias(physical);
        previous.stream().filter(old -> !old.equals(physical)).forEach(indexService::deleteIndex);

        long took = System.currentTimeMillis() - start;
        log.info("全量重建完成 index={} indexed={} took={}ms", physical, indexed, took);
        return new ReindexResult(physical, indexed, took);
    }
}
