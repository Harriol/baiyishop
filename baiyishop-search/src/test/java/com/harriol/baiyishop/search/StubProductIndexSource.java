package com.harriol.baiyishop.search;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.search.dto.IndexDocView;
import com.harriol.baiyishop.search.source.ProductIndexSource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试替身：用内存 Map 顶掉「回调 product-service」这一步，
 * 让增量同步 / 全量重建的编排逻辑可以独立验证（不必起 product-service）。
 */
class StubProductIndexSource implements ProductIndexSource {

    final Map<Long, IndexDocView> docs = new ConcurrentHashMap<>();
    final AtomicInteger fetchCount = new AtomicInteger();

    /** 非空时模拟商品服务不可用 */
    volatile RuntimeException failure;

    @Override
    public Optional<IndexDocView> fetch(long productId) {
        fetchCount.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        return Optional.ofNullable(docs.get(productId));
    }

    @Override
    public PageResult<IndexDocView> fetchPage(long page, long size) {
        List<IndexDocView> all = docs.values().stream()
                .sorted(Comparator.comparing(IndexDocView::id))
                .toList();
        int from = (int) Math.min((page - 1) * size, all.size());
        int to = (int) Math.min(from + size, all.size());
        return PageResult.of(page, size, all.size(), all.subList(from, to));
    }
}
