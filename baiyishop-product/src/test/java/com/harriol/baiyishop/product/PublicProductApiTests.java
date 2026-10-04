package com.harriol.baiyishop.product;

import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前台商品详情与分类商品列表端到端验证（REQ-205、REQ-206）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicProductApiTests {

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private HttpClient http;
    private String base;
    private String operatorToken;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
        operatorToken = tokenProvider.createAccessToken(1001L, Audience.ADMIN, "OPERATOR");
    }

    private record Resp(int status, JsonNode json) {
    }

    private Resp send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), objectMapper.readTree(response.body()));
    }

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private long createCategory(Long parentId, String categoryName) throws Exception {
        return send("POST", "/api/v1/admin/categories",
                "{\"parentId\":" + (parentId == null ? 0 : parentId) + ",\"name\":\"" + categoryName + "\"}",
                operatorToken).json().get("data").get("id").asLong();
    }

    private long createProduct(long categoryId, long price, boolean onSale) throws Exception {
        String body = "{\"name\":\"" + name("前台商品") + "\",\"categoryId\":" + categoryId + ","
                + "\"mainImage\":\"https://minio/main.jpg\","
                + "\"images\":[\"https://minio/1.jpg\",\"https://minio/2.jpg\"],"
                + "\"detail\":\"<p>详情</p>\",\"onSale\":" + onSale + ","
                + "\"skus\":[{\"specName\":\"默认规格\",\"price\":" + price + "}]}";
        Resp resp = send("POST", "/api/v1/admin/products", body, operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("id").asLong();
    }

    @Test
    @DisplayName("分类商品列表含子分类：挂在三级分类的商品，查一级分类也能看到")
    void listIncludesDescendantCategories() throws Exception {
        long level1 = createCategory(null, name("一级"));
        long level2 = createCategory(level1, name("二级"));
        long level3 = createCategory(level2, name("三级"));
        long productId = createProduct(level3, 1000, true);

        Resp resp = send("GET", "/api/v1/categories/" + level1 + "/products", null, null);
        assertThat(resp.status()).isEqualTo(200);
        JsonNode data = resp.json().get("data");

        boolean found = false;
        for (JsonNode item : data.get("list")) {
            if (item.get("id").asLong() == productId) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("分类商品列表只返回上架商品")
    void listOnlyReturnsOnSale() throws Exception {
        long categoryId = createCategory(null, name("叶子"));
        long onSaleId = createProduct(categoryId, 1000, true);
        long offSaleId = createProduct(categoryId, 2000, false);

        JsonNode list = send("GET", "/api/v1/categories/" + categoryId + "/products?size=100", null, null)
                .json().get("data").get("list");
        boolean hasOnSale = false;
        boolean hasOffSale = false;
        for (JsonNode item : list) {
            if (item.get("id").asLong() == onSaleId) {
                hasOnSale = true;
            }
            if (item.get("id").asLong() == offSaleId) {
                hasOffSale = true;
            }
        }
        assertThat(hasOnSale).isTrue();
        assertThat(hasOffSale).isFalse();
    }

    @Test
    @DisplayName("分类商品列表支持价格升降序与分页字段")
    void listSortAndPagination() throws Exception {
        long categoryId = createCategory(null, name("排序"));
        long cheap = createProduct(categoryId, 100, true);
        Thread.sleep(30);
        long expensive = createProduct(categoryId, 99000, true);

        JsonNode asc = send("GET", "/api/v1/categories/" + categoryId + "/products?sort=price_asc&size=100", null, null)
                .json().get("data");
        assertThat(asc.get("list").get(0).get("id").asLong()).isEqualTo(cheap);

        JsonNode desc = send("GET", "/api/v1/categories/" + categoryId + "/products?sort=price_desc&size=100", null, null)
                .json().get("data");
        assertThat(desc.get("list").get(0).get("id").asLong()).isEqualTo(expensive);

        JsonNode newest = send("GET", "/api/v1/categories/" + categoryId + "/products?sort=new&size=100", null, null)
                .json().get("data");
        assertThat(newest.get("list").get(0).get("id").asLong()).isEqualTo(expensive);

        assertThat(asc.get("page").asLong()).isEqualTo(1);
        assertThat(asc.get("size").asLong()).isEqualTo(100);
        assertThat(asc.get("total").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("空分类返回空列表而不是报错")
    void emptyCategoryReturnsEmptyList() throws Exception {
        long categoryId = createCategory(null, name("空分类"));
        Resp resp = send("GET", "/api/v1/categories/" + categoryId + "/products", null, null);
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.json().get("code").asInt()).isZero();
        assertThat(resp.json().get("data").get("list").size()).isZero();
        assertThat(resp.json().get("data").get("total").asLong()).isZero();
    }

    @Test
    @DisplayName("商品详情：图集、SKU、分类路径齐全；库存字段为 null 而非假的 0")
    void detailShape() throws Exception {
        long level1 = createCategory(null, name("详情一级"));
        long level2 = createCategory(level1, name("详情二级"));
        long productId = createProduct(level2, 12345, true);

        Resp resp = send("GET", "/api/v1/products/" + productId, null, null);
        assertThat(resp.status()).isEqualTo(200);
        JsonNode data = resp.json().get("data");

        assertThat(data.get("status").asString()).isEqualTo("ON_SALE");
        assertThat(data.get("minPrice").asLong()).isEqualTo(12345);
        assertThat(data.get("images").size()).isEqualTo(2);
        assertThat(data.get("skus").size()).isEqualTo(1);
        assertThat(data.get("skus").get(0).get("skuCode").asString()).isEqualTo(productId + "-01");
        assertThat(data.get("params").size()).isZero();
        assertThat(data.get("categoryPath").asString()).isEqualTo("/" + level1 + "/" + level2 + "/");
        // 库存权威在 inventory-service，未接入前必须是 null，不能伪造 0
        assertThat(data.get("available").isNull()).isTrue();
    }

    @Test
    @DisplayName("已下架商品仍能查看详情并返回 OFF_SALE（提示而非报错，REQ-206）")
    void offSaleProductStillViewable() throws Exception {
        long categoryId = createCategory(null, name("下架"));
        long productId = createProduct(categoryId, 100, true);
        send("PUT", "/api/v1/admin/products/" + productId + "/off-sale", null, operatorToken);

        Resp resp = send("GET", "/api/v1/products/" + productId, null, null);
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.json().get("code").asInt()).isZero();
        assertThat(resp.json().get("data").get("status").asString()).isEqualTo("OFF_SALE");
    }

    @Test
    @DisplayName("商品不存在返回 30007")
    void notFound() throws Exception {
        Resp resp = send("GET", "/api/v1/products/99999999", null, null);
        assertThat(resp.json().get("code").asInt()).isEqualTo(30007);
    }

    @Test
    @DisplayName("前台接口都不需要令牌")
    void publicWithoutToken() throws Exception {
        assertThat(send("GET", "/api/v1/categories/tree", null, null).status()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/brands", null, null).status()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/categories/1/products", null, null).status()).isEqualTo(200);
    }
}