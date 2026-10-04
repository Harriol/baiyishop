package com.harriol.baiyishop.search;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.search.dto.IndexDocView;
import com.harriol.baiyishop.search.dto.SearchItem;
import com.harriol.baiyishop.search.index.ProductSearchService;
import com.harriol.baiyishop.search.source.ProductIndexSource;
import com.harriol.baiyishop.search.sync.ProductChangeSyncService;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商品变更 → 索引同步的消费逻辑验证（REQ-302、docs/adr/ADR-005）。
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchSyncTests {

    private static final String ALIAS = "baiyishop_product_it_sync_" + Long.toHexString(System.currentTimeMillis());

    private static final AtomicLong CATEGORY_SEQ = new AtomicLong(800_000L);
    private static final AtomicLong ID_SEQ = new AtomicLong(9_000_000L);

    @DynamicPropertySource
    static void searchProperties(DynamicPropertyRegistry registry) {
        registry.add("baiyishop.search.index-alias", () -> ALIAS);
    }

    @TestConfiguration
    static class StubSourceConfig {
        @Bean
        @Primary
        StubProductIndexSource stubProductIndexSource() {
            return new StubProductIndexSource();
        }
    }

    @Autowired
    private StubProductIndexSource source;

    @Autowired
    private ProductChangeSyncService syncService;

    @Autowired
    private ProductSearchService searchService;

    @Autowired
    private RestClient restClient;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    private long categoryId;

    @BeforeEach
    void setUp() {
        categoryId = CATEGORY_SEQ.incrementAndGet();
    }

    @AfterAll
    void cleanUp() throws Exception {
        TestIndexCleanup.deleteAll(restClient, objectMapper, ALIAS + "_*");
    }

    private static String event(String action, long productId) {
        return "{\"eventId\":\"" + UUID.randomUUID().toString().replace("-", "") + "\",\"productId\":" + productId
                + ",\"action\":\"" + action + "\",\"occurredAt\":\"" + LocalDateTime.now() + "\"}";
    }

    private IndexDocView doc(long id) {
        return new IndexDocView(id, "同步商品" + id, "https://minio/" + id + ".jpg", 6600L, 3,
                categoryId, "/1/12/" + categoryId + "/", "同步分类", null, null,
                "ON_SALE", LocalDateTime.now(), LocalDateTime.now());
    }

    private List<SearchItem> searchAll() {
        PageResult<SearchItem> result = searchService.search(null, categoryId, null, null, null, "sales", 1, 50);
        return result.getList();
    }

    @Test
    @DisplayName("UPSERT 事件按 productId 重建索引文档；重复事件被幂等挡掉")
    void upsertEventIndexesDocument() {
        long productId = ID_SEQ.incrementAndGet();
        source.docs.put(productId, doc(productId));

        String message = event("UPSERT", productId);
        int before = source.fetchCount.get();
        syncService.handle(message);

        assertThat(searchAll()).extracting(SearchItem::id).contains(productId);
        assertThat(source.fetchCount.get() - before).isEqualTo(1);

        // 同一条消息重投（RocketMQ 至少一次语义）不应重复回查
        syncService.handle(message);
        assertThat(source.fetchCount.get() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE 事件把文档移出索引")
    void deleteEventRemovesDocument() {
        long productId = ID_SEQ.incrementAndGet();
        source.docs.put(productId, doc(productId));
        syncService.handle(event("UPSERT", productId));
        assertThat(searchAll()).extracting(SearchItem::id).contains(productId);

        syncService.handle(event("DELETE", productId));
        assertThat(searchAll()).extracting(SearchItem::id).doesNotContain(productId);
    }

    @Test
    @DisplayName("回查不到商品（已删除）时同样移除文档，并状态一致")
    void missingProductRemovesDocument() {
        long productId = ID_SEQ.incrementAndGet();
        source.docs.put(productId, doc(productId));
        syncService.handle(event("UPSERT", productId));
        assertThat(searchAll()).extracting(SearchItem::id).contains(productId);

        // 商品被逻辑删除：索引文档也应消失，而不是留一条查不到的幽灵记录
        source.docs.remove(productId);
        syncService.handle(event("UPSERT", productId));
        assertThat(searchAll()).extracting(SearchItem::id).doesNotContain(productId);
    }

    @Test
    @DisplayName("同步失败向上抛（触发 MQ 重投），且不会被幂等标记吞掉")
    void failurePropagatesAndCanBeRetried() {
        long productId = ID_SEQ.incrementAndGet();
        source.docs.put(productId, doc(productId));
        String message = event("UPSERT", productId);

        source.failure = new IllegalStateException("商品服务暂时不可用");
        int before = source.fetchCount.get();
        assertThatThrownBy(() -> syncService.handle(message)).isInstanceOf(IllegalStateException.class);
        assertThat(searchAll()).isEmpty();

        // 重投必须能真正重试：若失败时已写幂等标记，这里会被挡掉，索引就永久落后了
        source.failure = null;
        syncService.handle(message);
        assertThat(source.fetchCount.get() - before).isEqualTo(2);
        assertThat(searchAll()).extracting(SearchItem::id).contains(productId);
    }
}
