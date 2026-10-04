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
 * 首页配置端到端验证（REQ-401 ~ REQ-403）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomeConfigApiTests {

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

    private long createLeafCategory() throws Exception {
        long root = send("POST", "/api/v1/admin/categories",
                "{\"parentId\":0,\"name\":\"" + name("根") + "\"}", operatorToken).json().get("data").get("id").asLong();
        return send("POST", "/api/v1/admin/categories",
                "{\"parentId\":" + root + ",\"name\":\"" + name("叶") + "\"}", operatorToken)
                .json().get("data").get("id").asLong();
    }

    private long createProduct(long categoryId, long price, boolean onSale) throws Exception {
        Resp resp = send("POST", "/api/v1/admin/products",
                "{\"name\":\"" + name("首页商品") + "\",\"categoryId\":" + categoryId + ","
                        + "\"mainImage\":\"https://minio/m.jpg\",\"onSale\":" + onSale + ","
                        + "\"skus\":[{\"price\":" + price + "}]}", operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("id").asLong();
    }

    @Test
    @DisplayName("轮播 / 公告 / 金刚区保存后可读回，且停用项不下发到前台")
    void saveAndReadConfig() throws Exception {
        String bannerTitle = name("轮播");
        assertThat(send("PUT", "/api/v1/admin/home/banners",
                "[{\"title\":\"" + bannerTitle + "\",\"imageUrl\":\"https://minio/b1.jpg\",\"linkType\":2,\"linkValue\":\"1\",\"sort\":1,\"enabled\":true},"
                        + "{\"title\":\"停用轮播\",\"imageUrl\":\"https://minio/b2.jpg\",\"enabled\":false}]", operatorToken)
                .json().get("code").asInt()).isZero();

        JsonNode saved = send("GET", "/api/v1/admin/home/banners", null, operatorToken).json().get("data");
        assertThat(saved.size()).isEqualTo(2);
        assertThat(saved.get(0).get("title").asString()).isEqualTo(bannerTitle);

        assertThat(send("PUT", "/api/v1/admin/home/notices",
                "[{\"content\":\"全场包邮\",\"sort\":1,\"enabled\":true}]", operatorToken).json().get("code").asInt()).isZero();

        JsonNode home = send("GET", "/api/v1/home", null, null).json().get("data");
        assertThat(home.get("banners").size()).isEqualTo(1);
        assertThat(home.get("banners").get(0).get("title").asString()).isEqualTo(bannerTitle);
        assertThat(home.get("notices").get(0).get("content").asString()).isEqualTo("全场包邮");
    }

    @Test
    @DisplayName("楼层按分类自动拉取商品，且只含上架商品")
    void floorAutoPullsProducts() throws Exception {
        long categoryId = createLeafCategory();
        long onSaleId = createProduct(categoryId, 1000, true);
        createProduct(categoryId, 2000, false);

        assertThat(send("PUT", "/api/v1/admin/home/floors",
                "[{\"title\":\"热销榜\",\"categoryId\":" + categoryId + ",\"sortField\":\"sales\",\"limitSize\":8,\"sort\":1,\"enabled\":true}]",
                operatorToken).json().get("code").asInt()).isZero();

        JsonNode floors = send("GET", "/api/v1/home", null, null).json().get("data").get("floors");
        assertThat(floors.size()).isEqualTo(1);
        assertThat(floors.get(0).get("sortField").asString()).isEqualTo("sales");
        assertThat(floors.get(0).get("products").size()).isEqualTo(1);
        assertThat(floors.get(0).get("products").get(0).get("id").asLong()).isEqualTo(onSaleId);
    }

    @Test
    @DisplayName("每个楼层可单独配置排序维度，前台按该维度拉取")
    void floorSortFieldIsPerFloor() throws Exception {
        long categoryId = createLeafCategory();
        long cheap = createProduct(categoryId, 100, true);
        Thread.sleep(30);
        long expensive = createProduct(categoryId, 50000, true);

        send("PUT", "/api/v1/admin/home/floors",
                "[{\"title\":\"价格升\",\"categoryId\":" + categoryId + ",\"sortField\":\"price_asc\",\"limitSize\":8,\"sort\":1,\"enabled\":true},"
                        + "{\"title\":\"价格降\",\"categoryId\":" + categoryId + ",\"sortField\":\"price_desc\",\"limitSize\":8,\"sort\":2,\"enabled\":true}]",
                operatorToken);

        JsonNode floors = send("GET", "/api/v1/home", null, null).json().get("data").get("floors");
        assertThat(floors.get(0).get("products").get(0).get("id").asLong()).isEqualTo(cheap);
        assertThat(floors.get(1).get("products").get(0).get("id").asLong()).isEqualTo(expensive);
    }

    @Test
    @DisplayName("分类下没有商品时楼层返回空数组，不报错")
    void emptyFloorDoesNotFail() throws Exception {
        long emptyCategory = createLeafCategory();
        send("PUT", "/api/v1/admin/home/floors",
                "[{\"title\":\"空楼层\",\"categoryId\":" + emptyCategory + ",\"sortField\":\"sales\",\"enabled\":true}]", operatorToken);

        Resp resp = send("GET", "/api/v1/home", null, null);
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.json().get("code").asInt()).isZero();
        assertThat(resp.json().get("data").get("floors").get(0).get("products").size()).isZero();
    }

    @Test
    @DisplayName("人工置顶：指定商品排在该楼层最前")
    void pinnedProductComesFirst() throws Exception {
        long categoryId = createLeafCategory();
        long normal = createProduct(categoryId, 100, true);
        Thread.sleep(30);
        long pinned = createProduct(categoryId, 90000, true);

        // 按销量排序时，置顶的商品本该排在后面
        send("PUT", "/api/v1/admin/home/floors",
                "[{\"title\":\"置顶楼层\",\"categoryId\":" + categoryId + ",\"sortField\":\"sales\",\"limitSize\":8,"
                        + "\"pinnedProductIds\":[" + pinned + "],\"enabled\":true}]", operatorToken);

        JsonNode products = send("GET", "/api/v1/home", null, null).json().get("data").get("floors").get(0).get("products");
        assertThat(products.get(0).get("id").asLong()).isEqualTo(pinned);
        assertThat(products.size()).isEqualTo(2);
        assertThat(products.get(1).get("id").asLong()).isEqualTo(normal);
    }

    @Test
    @DisplayName("配置为空时首页正常返回空数组（REQ-403）")
    void emptyConfigIsSafe() throws Exception {
        send("PUT", "/api/v1/admin/home/banners", "[]", operatorToken);
        send("PUT", "/api/v1/admin/home/notices", "[]", operatorToken);
        send("PUT", "/api/v1/admin/home/navs", "[]", operatorToken);
        send("PUT", "/api/v1/admin/home/floors", "[]", operatorToken);

        Resp resp = send("GET", "/api/v1/home", null, null);
        assertThat(resp.status()).isEqualTo(200);
        JsonNode data = resp.json().get("data");
        assertThat(data.get("banners").size()).isZero();
        assertThat(data.get("notices").size()).isZero();
        assertThat(data.get("navs").size()).isZero();
        assertThat(data.get("floors").size()).isZero();
    }

    @Test
    @DisplayName("金刚区与楼层绑定不存在的分类返回 30001；置顶不存在的商品返回 30007")
    void invalidReferencesRejected() throws Exception {
        assertThat(send("PUT", "/api/v1/admin/home/navs",
                "[{\"name\":\"数码\",\"categoryId\":99999999}]", operatorToken).json().get("code").asInt()).isEqualTo(30001);
        assertThat(send("PUT", "/api/v1/admin/home/floors",
                "[{\"title\":\"楼层\",\"categoryId\":99999999}]", operatorToken).json().get("code").asInt()).isEqualTo(30001);
        assertThat(send("PUT", "/api/v1/admin/home/floors",
                "[{\"title\":\"楼层\",\"categoryId\":" + createLeafCategory() + ",\"pinnedProductIds\":[99999999]}]", operatorToken)
                .json().get("code").asInt()).isEqualTo(30007);
    }

    @Test
    @DisplayName("角色校验：客服 403、无令牌 401、前台首页公开")
    void roleEnforcement() throws Exception {
        assertThat(send("GET", "/api/v1/admin/home/banners", null, null).status()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/admin/home/banners", null, serviceToken).status()).isEqualTo(403);
        assertThat(send("PUT", "/api/v1/admin/home/banners", "[]", serviceToken).status()).isEqualTo(403);
        assertThat(send("GET", "/api/v1/home", null, null).status()).isEqualTo(200);
    }
}