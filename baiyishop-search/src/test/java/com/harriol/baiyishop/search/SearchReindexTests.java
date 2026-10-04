package com.harriol.baiyishop.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.harriol.baiyishop.search.dto.IndexDocView;
import com.harriol.baiyishop.search.dto.ReindexResult;
import com.harriol.baiyishop.search.index.ProductSearchService;
import com.harriol.baiyishop.search.index.SearchReindexService;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全量重建索引验证（REQ-302）：写临时索引 → 原子切换别名 → 删旧索引，重建期间查询不中断。
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchReindexTests {

    private static final String ALIAS = "baiyishop_product_it_reindex_" + Long.toHexString(System.currentTimeMillis());

    private static final AtomicLong CATEGORY_SEQ = new AtomicLong(950_000L);
    private static final AtomicLong ID_SEQ = new AtomicLong(9_500_000L);

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
    private SearchReindexService reindexService;

    @Autowired
    private ProductSearchService searchService;

    @Autowired
    private ElasticsearchClient client;

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

    private void seed(int count) {
        for (int i = 0; i < count; i++) {
            long id = ID_SEQ.incrementAndGet();
            source.docs.put(id, new IndexDocView(id, "重建商品" + id, "https://minio/" + id + ".jpg", 1234L, i,
                    categoryId, "/1/12/" + categoryId + "/", "重建分类", null, null,
                    "ON_SALE", LocalDateTime.now(), LocalDateTime.now()));
        }
    }

    private Set<String> aliasIndices() throws Exception {
        return client.indices().getAlias(g -> g.name(ALIAS)).result().keySet();
    }

    @Test
    @DisplayName("全量重建：分页拉取写入新索引，最后原子切换别名并删除旧索引")
    void reindexWritesNewIndexAndSwapsAlias() throws Exception {
        seed(5);
        int expected = source.docs.size();

        ReindexResult result = reindexService.reindex(2);

        assertThat(result.indexed()).isEqualTo(expected);
        assertThat(result.index()).startsWith(ALIAS + "_");
        assertThat(aliasIndices()).containsExactly(result.index());
        // 启动时自动建的 _v1 已被切换并清理
        assertThat(client.indices().exists(e -> e.index(ALIAS + "_v1")).value()).isFalse();
        assertThat(searchService.search(null, null, null, null, null, "composite", 1, 100).getTotal())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("重复重建：每次生成独立物理索引，重建后别名只指向最新索引")
    void reindexIsRepeatable() throws Exception {
        seed(3);

        ReindexResult first = reindexService.reindex(2);
        ReindexResult second = reindexService.reindex(2);

        assertThat(second.index()).isNotEqualTo(first.index());
        assertThat(aliasIndices()).containsExactly(second.index());
        assertThat(client.indices().exists(e -> e.index(first.index())).value()).isFalse();
    }
}
