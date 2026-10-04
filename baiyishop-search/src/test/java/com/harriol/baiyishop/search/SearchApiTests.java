package com.harriol.baiyishop.search;

import com.harriol.baiyishop.search.dto.IndexDocView;
import com.harriol.baiyishop.search.index.SearchIndexService;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品搜索接口端到端验证（REQ-301）。
 * <p>每个用例用自己的分类 ID 圈定数据，彼此不干扰；索引别名按测试随机，避免污染真实索引。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchApiTests {

    private static final String ALIAS = "baiyishop_product_it_" + Long.toHexString(System.currentTimeMillis());

    private static final AtomicLong ID_SEQ = new AtomicLong(8_000_000L);
    private static final AtomicLong CATEGORY_SEQ = new AtomicLong(700_000L);

    @DynamicPropertySource
    static void searchProperties(DynamicPropertyRegistry registry) {
        registry.add("baiyishop.search.index-alias", () -> ALIAS);
    }

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestClient restClient;

    private HttpClient http;
    private String base;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
    }

    @AfterAll
    void cleanUp() throws Exception {
        TestIndexCleanup.deleteAll(restClient, objectMapper, ALIAS + "_*");
    }

    private record Resp(int status, JsonNode json) {
    }

    private Resp get(String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), objectMapper.readTree(response.body()));
    }

    private static long nextCategoryId() {
        return CATEGORY_SEQ.incrementAndGet();
    }

    private long index(String name, long categoryId, Long brandId, long price, int sales,
                       String status, LocalDateTime onSaleTime) {
        long id = ID_SEQ.incrementAndGet();
        indexService.upsert(new IndexDocView(id, name, "https://minio/" + id + ".jpg", price, sales,
                categoryId, "/1/12/" + categoryId + "/", "测试分类", brandId, brandId == null ? null : "测试品牌",
                status, onSaleTime, LocalDateTime.now()));
        return id;
    }

    @Test
    @DisplayName("关键词命中商品名称；不匹配时返回空列表与 total=0（前端空态）")
    void keywordSearchHitsProductName() throws Exception {
        long categoryId = nextCategoryId();
        long tee = index("纯棉圆领T恤", categoryId, null, 9900, 10, "ON_SALE", LocalDateTime.now());
        index("不锈钢保温杯", categoryId, null, 5900, 20, "ON_SALE", LocalDateTime.now());

        JsonNode data = get("/api/v1/search/products?keyword=纯棉T恤&categoryId=" + categoryId
                + "&sort=composite").json().get("data");
        assertThat(data.get("total").asLong()).isEqualTo(1);
        assertThat(data.get("list").get(0).get("id").asLong()).isEqualTo(tee);
        assertThat(data.get("list").get(0).get("name").asString()).isEqualTo("纯棉圆领T恤");
        assertThat(data.get("list").get(0).get("price").asLong()).isEqualTo(9900);
        assertThat(data.get("list").get(0).get("mainImage").asString()).isEqualTo("https://minio/" + tee + ".jpg");

        JsonNode empty = get("/api/v1/search/products?keyword=完全不存在的东西&categoryId=" + categoryId)
                .json().get("data");
        assertThat(empty.get("total").asLong()).isZero();
        assertThat(empty.get("list").size()).isZero();
    }

    @Test
    @DisplayName("分类筛选含子分类：命中祖先分类即命中所有子孙")
    void categoryFilterIncludesDescendants() throws Exception {
        long parent = nextCategoryId();
        long child1 = nextCategoryId();
        long child2 = nextCategoryId();
        long first = indexWithPath("子分类商品一", child1, "/1/" + parent + "/" + child1 + "/");
        long second = indexWithPath("子分类商品二", child2, "/1/" + parent + "/" + child2 + "/");
        // 父分类自身没有商品，按父分类筛选仍应带出两个子分类的商品（REQ-205 的语义）
        indexWithPath("无关分类商品", nextCategoryId(), "/1/999999/");

        JsonNode list = get("/api/v1/search/products?categoryId=" + parent + "&size=50").json()
                .get("data").get("list");
        assertThat(idsOf(list)).contains(first, second);
    }

    @Test
    @DisplayName("品牌筛选 + 价格区间筛选")
    void filtersByBrandAndPriceRange() throws Exception {
        long categoryId = nextCategoryId();
        long brandA = 910_001L;
        long brandB = 910_002L;
        long cheapA = index("品牌A 便宜", categoryId, brandA, 1000, 1, "ON_SALE", LocalDateTime.now());
        long expensiveA = index("品牌A 贵", categoryId, brandA, 30000, 1, "ON_SALE", LocalDateTime.now());
        long cheapB = index("品牌B 便宜", categoryId, brandB, 1000, 1, "ON_SALE", LocalDateTime.now());

        JsonNode byBrand = get("/api/v1/search/products?categoryId=" + categoryId + "&brandId=" + brandA
                + "&size=50").json().get("data").get("list");
        assertThat(idsOf(byBrand)).containsExactlyInAnyOrder(cheapA, expensiveA);

        JsonNode byRange = get("/api/v1/search/products?categoryId=" + categoryId
                + "&minPrice=500&maxPrice=5000&size=50").json().get("data").get("list");
        assertThat(idsOf(byRange)).containsExactlyInAnyOrder(cheapA, cheapB);
    }

    @Test
    @DisplayName("下架商品留在索引但不被搜到（重新上架可立即恢复）")
    void offSaleProductsAreHidden() throws Exception {
        long categoryId = nextCategoryId();
        long onSale = index("在售商品", categoryId, null, 1000, 1, "ON_SALE", LocalDateTime.now());
        long offSale = index("下架商品", categoryId, null, 1000, 1, "OFF_SALE", LocalDateTime.now());

        JsonNode list = get("/api/v1/search/products?categoryId=" + categoryId + "&size=50").json()
                .get("data").get("list");
        assertThat(idsOf(list)).contains(onSale).doesNotContain(offSale);
    }

    @Test
    @DisplayName("销量排序与价格升降序")
    void sortsBySalesAndPrice() throws Exception {
        long categoryId = nextCategoryId();
        long lowSales = index("低销量", categoryId, null, 5000, 1, "ON_SALE", LocalDateTime.now());
        long highSales = index("高销量", categoryId, null, 9000, 500, "ON_SALE", LocalDateTime.now());

        JsonNode bySales = get("/api/v1/search/products?categoryId=" + categoryId + "&sort=sales&size=50")
                .json().get("data").get("list");
        assertThat(idsOf(bySales)).containsExactly(highSales, lowSales);

        JsonNode byPriceAsc = get("/api/v1/search/products?categoryId=" + categoryId + "&sort=price_asc&size=50")
                .json().get("data").get("list");
        assertThat(idsOf(byPriceAsc)).containsExactly(lowSales, highSales);

        JsonNode byPriceDesc = get("/api/v1/search/products?categoryId=" + categoryId + "&sort=price_desc&size=50")
                .json().get("data").get("list");
        assertThat(idsOf(byPriceDesc)).containsExactly(highSales, lowSales);
    }

    @Test
    @DisplayName("综合排序：销量高者靠前（log 归一化 × 0.6）")
    void compositeSortPutsHighSalesFirst() throws Exception {
        long categoryId = nextCategoryId();
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        long popular = index("热销商品", categoryId, null, 1000, 1000, "ON_SALE", yesterday);
        long normal = index("普通商品", categoryId, null, 1000, 100, "ON_SALE", yesterday);

        JsonNode list = get("/api/v1/search/products?categoryId=" + categoryId + "&sort=composite&size=50")
                .json().get("data").get("list");
        assertThat(idsOf(list)).containsExactly(popular, normal);
    }

    @Test
    @DisplayName("综合排序：上架新鲜度 × 0.4 可以压过销量")
    void compositeSortRewardsFreshOnSale() throws Exception {
        long categoryId = nextCategoryId();
        // 高销量但上架 400 天（新鲜度 0）vs 低销量但今天上架（新鲜度 1）
        long oldPopular = index("老爆款", categoryId, null, 1000, 1000, "ON_SALE", LocalDateTime.now().minusDays(400));
        long freshNew = index("今日新品", categoryId, null, 1000, 100, "ON_SALE", LocalDateTime.now());

        JsonNode list = get("/api/v1/search/products?categoryId=" + categoryId + "&sort=composite&size=50")
                .json().get("data").get("list");
        assertThat(idsOf(list)).containsExactly(freshNew, oldPopular);
    }

    @Test
    @DisplayName("分页：total 为命中总数，list 按页返回")
    void paginates() throws Exception {
        long categoryId = nextCategoryId();
        for (int i = 1; i <= 5; i++) {
            index("分页商品" + i, categoryId, null, 1000 + i, i, "ON_SALE", LocalDateTime.now());
        }

        JsonNode first = get("/api/v1/search/products?categoryId=" + categoryId + "&sort=sales&page=1&size=2")
                .json().get("data");
        assertThat(first.get("total").asLong()).isEqualTo(5);
        assertThat(first.get("list").size()).isEqualTo(2);
        assertThat(first.get("list").get(0).get("sales").asInt()).isEqualTo(5);

        JsonNode third = get("/api/v1/search/products?categoryId=" + categoryId + "&sort=sales&page=3&size=2")
                .json().get("data");
        assertThat(third.get("list").size()).isEqualTo(1);
    }

    private long indexWithPath(String name, long categoryId, String categoryPath) {
        long id = ID_SEQ.incrementAndGet();
        indexService.upsert(new IndexDocView(id, name, "https://minio/" + id + ".jpg", 1000L, 1,
                categoryId, categoryPath, "测试分类", null, null, "ON_SALE", LocalDateTime.now(),
                LocalDateTime.now()));
        return id;
    }

    private List<Long> idsOf(JsonNode list) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : list) {
            ids.add(item.get("id").asLong());
        }
        return ids;
    }
}
