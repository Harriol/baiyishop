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
 * 后台商品管理端到端验证（REQ-203、REQ-206）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductApiTests {

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private HttpClient http;
    private String base;
    private String operatorToken;
    private String serviceToken;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
        operatorToken = tokenProvider.createAccessToken(1001L, Audience.ADMIN, "OPERATOR");
        serviceToken = tokenProvider.createAccessToken(1002L, Audience.ADMIN, "SERVICE");
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

    /** 建一棵「一级 + 二级」分类，返回二级（叶子）分类 ID */
    private long createLeafCategory() throws Exception {
        long root = send("POST", "/api/v1/admin/categories",
                "{\"parentId\":0,\"name\":\"" + name("根") + "\"}", operatorToken).json().get("data").get("id").asLong();
        return send("POST", "/api/v1/admin/categories",
                "{\"parentId\":" + root + ",\"name\":\"" + name("叶") + "\"}", operatorToken)
                .json().get("data").get("id").asLong();
    }

    private long createBrand() throws Exception {
        return send("POST", "/api/v1/admin/brands", "{\"name\":\"" + name("品牌") + "\"}", operatorToken)
                .json().get("data").get("id").asLong();
    }

    private String productBody(String productName, long categoryId, Long brandId, long price, int skuCount) {
        StringBuilder skus = new StringBuilder();
        for (int i = 0; i < skuCount; i++) {
            if (i > 0) {
                skus.append(",");
            }
            skus.append("{\"specName\":\"默认规格\",\"price\":").append(price + i * 100).append("}");
        }
        return "{\"name\":\"" + productName + "\",\"categoryId\":" + categoryId + ","
                + "\"brandId\":" + (brandId == null ? "null" : brandId) + ","
                + "\"mainImage\":\"https://minio/main.jpg\","
                + "\"images\":[\"https://minio/1.jpg\",\"https://minio/2.jpg\"],"
                + "\"detail\":\"<p>详情</p>\",\"onSale\":false,\"skus\":[" + skus + "]}";
    }

    private long createProduct(long categoryId, Long brandId, long price, int skuCount) throws Exception {
        Resp resp = send("POST", "/api/v1/admin/products",
                productBody(name("商品"), categoryId, brandId, price, skuCount), operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("id").asLong();
    }

    @Test
    @DisplayName("新增商品：SKU 编码自动生成、minPrice 取最低价、默认下架、图集已存")
    void createProductGeneratesSkuCodes() throws Exception {
        long categoryId = createLeafCategory();
        Resp resp = send("POST", "/api/v1/admin/products",
                productBody(name("商品"), categoryId, null, 9900, 1), operatorToken);

        JsonNode data = resp.json().get("data");
        long id = data.get("id").asLong();
        assertThat(data.get("status").asString()).isEqualTo("OFF_SALE");
        assertThat(data.get("minPrice").asLong()).isEqualTo(9900);
        assertThat(data.get("images").size()).isEqualTo(2);
        assertThat(data.get("skus").get(0).get("skuCode").asString()).isEqualTo(id + "-01");
        assertThat(data.get("skus").get(0).get("price").asLong()).isEqualTo(9900);
    }

    @Test
    @DisplayName("商品只能挂在叶子分类上")
    void productMustUseLeafCategory() throws Exception {
        long root = send("POST", "/api/v1/admin/categories",
                "{\"parentId\":0,\"name\":\"" + name("非叶") + "\"}", operatorToken).json().get("data").get("id").asLong();
        send("POST", "/api/v1/admin/categories", "{\"parentId\":" + root + ",\"name\":\"" + name("子") + "\"}", operatorToken);

        Resp resp = send("POST", "/api/v1/admin/products", productBody(name("商品"), root, null, 100, 1), operatorToken);
        assertThat(resp.status()).isEqualTo(400);
        assertThat(resp.json().get("message").asString()).contains("叶子分类");
    }

    @Test
    @DisplayName("分类或品牌不存在时被拒（30001 / 30005）")
    void invalidCategoryOrBrandRejected() throws Exception {
        long categoryId = createLeafCategory();
        assertThat(send("POST", "/api/v1/admin/products", productBody(name("商品"), 99999999L, null, 100, 1), operatorToken)
                .json().get("code").asInt()).isEqualTo(30001);
        assertThat(send("POST", "/api/v1/admin/products", productBody(name("商品"), categoryId, 99999999L, 100, 1), operatorToken)
                .json().get("code").asInt()).isEqualTo(30005);
    }

    @Test
    @DisplayName("上下架切换状态并记录上架时间")
    void toggleOnSale() throws Exception {
        long id = createProduct(createLeafCategory(), null, 100, 1);

        JsonNode onSale = send("PUT", "/api/v1/admin/products/" + id + "/on-sale", null, operatorToken).json().get("data");
        assertThat(onSale.get("status").asString()).isEqualTo("ON_SALE");
        assertThat(onSale.get("onSaleTime").isNull()).isFalse();

        JsonNode offSale = send("PUT", "/api/v1/admin/products/" + id + "/off-sale", null, operatorToken).json().get("data");
        assertThat(offSale.get("status").asString()).isEqualTo("OFF_SALE");
    }

    @Test
    @DisplayName("修改商品：SKU 数量增减都不会撞唯一索引，多余的被逻辑删除")
    void updateSkusWithoutUniqueConflict() throws Exception {
        long categoryId = createLeafCategory();
        long id = createProduct(categoryId, null, 9900, 1);

        // 1 个 SKU 扩到 2 个
        Resp expanded = send("PUT", "/api/v1/admin/products/" + id,
                productBody(name("改"), categoryId, null, 8800, 2), operatorToken);
        assertThat(expanded.json().get("code").asInt()).isZero();
        JsonNode skus2 = expanded.json().get("data").get("skus");
        assertThat(skus2.size()).isEqualTo(2);
        assertThat(skus2.get(0).get("skuCode").asString()).isEqualTo(id + "-01");
        assertThat(skus2.get(1).get("skuCode").asString()).isEqualTo(id + "-02");
        assertThat(expanded.json().get("data").get("minPrice").asLong()).isEqualTo(8800);

        // 再缩回 1 个：这一步以前会因为逻辑删除的行仍占用 sku_code 而报唯一索引冲突
        Resp shrunk = send("PUT", "/api/v1/admin/products/" + id,
                productBody(name("改2"), categoryId, null, 7700, 1), operatorToken);
        assertThat(shrunk.json().get("code").asInt()).isZero();
        JsonNode skus1 = shrunk.json().get("data").get("skus");
        assertThat(skus1.size()).isEqualTo(1);
        assertThat(skus1.get(0).get("skuCode").asString()).isEqualTo(id + "-01");
        assertThat(skus1.get(0).get("price").asLong()).isEqualTo(7700);
    }

    @Test
    @DisplayName("删除商品为逻辑删除，之后查询返回 30007")
    void deleteIsLogical() throws Exception {
        long id = createProduct(createLeafCategory(), null, 100, 1);
        assertThat(send("DELETE", "/api/v1/admin/products/" + id, null, operatorToken).json().get("code").asInt()).isZero();
        assertThat(send("GET", "/api/v1/admin/products/" + id, null, operatorToken).json().get("code").asInt()).isEqualTo(30007);
    }

    @Test
    @DisplayName("后台列表按关键词与状态筛选")
    void pageFilters() throws Exception {
        long categoryId = createLeafCategory();
        String productName = name("筛选");
        long id = createProduct(categoryId, null, 100, 1);
        send("PUT", "/api/v1/admin/products/" + id, productBody(productName, categoryId, null, 100, 1), operatorToken);

        Resp byKeyword = send("GET", "/api/v1/admin/products?page=1&size=10&keyword=" + productName, null, operatorToken);
        assertThat(byKeyword.json().get("data").get("total").asLong()).isEqualTo(1);

        Resp byStatus = send("GET", "/api/v1/admin/products?page=1&size=10&categoryId=" + categoryId + "&status=OFF_SALE", null, operatorToken);
        assertThat(byStatus.json().get("data").get("total").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("角色校验：客服 403、无令牌 401")
    void roleEnforcement() throws Exception {
        assertThat(send("GET", "/api/v1/admin/products", null, null).status()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/admin/products", null, serviceToken).status()).isEqualTo(403);
        assertThat(send("POST", "/api/v1/admin/products", "{}", serviceToken).status()).isEqualTo(403);
    }
}