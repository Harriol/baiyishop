package com.harriol.baiyishop.product;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.product.entity.MqOutbox;
import com.harriol.baiyishop.product.mapper.MqOutboxMapper;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品索引同步的发布端验证（REQ-302、docs/adr/ADR-005）：
 * <ul>
 *   <li>商品新增 / 修改 / 上下架 / 删除都在同一事务内写一条 mq_outbox</li>
 *   <li>索引文档内部接口的字段与 30007 语义（供 search-service 增量同步与全量重建）</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IndexSyncOutboxTests {

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private MqOutboxMapper outboxMapper;

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

    private long createLeafCategory() throws Exception {
        long root = send("POST", "/api/v1/admin/categories",
                "{\"parentId\":0,\"name\":\"" + name("根") + "\"}", operatorToken).json().get("data").get("id").asLong();
        return send("POST", "/api/v1/admin/categories",
                "{\"parentId\":" + root + ",\"name\":\"" + name("叶") + "\"}", operatorToken)
                .json().get("data").get("id").asLong();
    }

    private String productBody(String productName, long categoryId, long price) {
        return "{\"name\":\"" + productName + "\",\"categoryId\":" + categoryId + ",\"brandId\":null,"
                + "\"mainImage\":\"https://minio/main.jpg\",\"images\":[\"https://minio/1.jpg\"],"
                + "\"detail\":\"<p>详情</p>\",\"onSale\":false,"
                + "\"skus\":[{\"specName\":\"默认规格\",\"price\":" + price + "}]}";
    }

    private long createProduct(long categoryId, long price) throws Exception {
        Resp resp = send("POST", "/api/v1/admin/products", productBody(name("商品"), categoryId, price), operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("id").asLong();
    }

    private List<MqOutbox> outboxOf(long productId) {
        return outboxMapper.selectList(Wrappers.<MqOutbox>lambdaQuery()
                .eq(MqOutbox::getBizKey, String.valueOf(productId))
                .orderByAsc(MqOutbox::getId));
    }

    @Test
    @DisplayName("新增 / 修改 / 上下架 / 删除都在同事务内写 mq_outbox，事件体带 eventId 与 action")
    void productChangesArePublishedToOutbox() throws Exception {
        long categoryId = createLeafCategory();
        long productId = createProduct(categoryId, 9900);

        List<MqOutbox> afterCreate = outboxOf(productId);
        assertThat(afterCreate).hasSize(1);
        MqOutbox created = afterCreate.get(0);
        assertThat(created.getTopic()).isEqualTo("baiyishop-product-changed");
        assertThat(created.getTag()).isEqualTo("UPSERT");
        assertThat(created.getStatus()).isEqualTo(MqOutbox.STATUS_PENDING);
        assertThat(created.getRetryCount()).isZero();
        JsonNode payload = objectMapper.readTree(created.getPayload());
        assertThat(payload.get("eventId").asString()).isEqualTo(created.getEventId());
        assertThat(payload.get("productId").asLong()).isEqualTo(productId);
        assertThat(payload.get("action").asString()).isEqualTo("UPSERT");
        assertThat(payload.get("occurredAt").asString()).isNotBlank();

        // 修改商品
        send("PUT", "/api/v1/admin/products/" + productId,
                productBody(name("改"), categoryId, 8800), operatorToken);
        assertThat(outboxOf(productId)).hasSize(2);

        // 上架 / 下架
        send("PUT", "/api/v1/admin/products/" + productId + "/on-sale", null, operatorToken);
        send("PUT", "/api/v1/admin/products/" + productId + "/off-sale", null, operatorToken);
        assertThat(outboxOf(productId)).hasSize(4);

        // 删除
        send("DELETE", "/api/v1/admin/products/" + productId, null, operatorToken);
        List<MqOutbox> all = outboxOf(productId);
        assertThat(all).hasSize(5);
        assertThat(all.get(4).getTag()).isEqualTo("DELETE");
        assertThat(objectMapper.readTree(all.get(4).getPayload()).get("action").asString()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("索引文档接口：字段齐全（分类路径、品牌名、价格取最低 SKU）")
    void indexDocCarriesSearchFields() throws Exception {
        long categoryId = createLeafCategory();
        String brand = name("品牌");
        long brandId = send("POST", "/api/v1/admin/brands", "{\"name\":\"" + brand + "\"}", operatorToken)
                .json().get("data").get("id").asLong();
        String productName = name("搜索商品");
        long productId = send("POST", "/api/v1/admin/products",
                "{\"name\":\"" + productName + "\",\"categoryId\":" + categoryId + ",\"brandId\":" + brandId + ","
                        + "\"mainImage\":\"https://minio/main.jpg\",\"images\":[],\"detail\":\"<p>d</p>\","
                        + "\"onSale\":true,\"skus\":[{\"specName\":\"默认规格\",\"price\":12900},"
                        + "{\"specName\":\"大号\",\"price\":15900}]}", operatorToken)
                .json().get("data").get("id").asLong();

        JsonNode doc = send("GET", "/internal/products/" + productId + "/index-doc", null, null).json().get("data");
        assertThat(doc.get("id").asLong()).isEqualTo(productId);
        assertThat(doc.get("name").asString()).isEqualTo(productName);
        assertThat(doc.get("price").asLong()).isEqualTo(12900);
        assertThat(doc.get("sales").asInt()).isZero();
        assertThat(doc.get("categoryId").asLong()).isEqualTo(categoryId);
        assertThat(doc.get("categoryPath").asString()).startsWith("/").endsWith("/");
        assertThat(doc.get("brandId").asLong()).isEqualTo(brandId);
        assertThat(doc.get("brandName").asString()).isEqualTo(brand);
        assertThat(doc.get("status").asString()).isEqualTo("ON_SALE");
        assertThat(doc.get("onSaleTime").asString()).isNotBlank();
    }

    @Test
    @DisplayName("索引文档接口：商品不存在 / 已删除返回 30007，搜索侧据此移除文档")
    void missingProductReturns30007() throws Exception {
        Resp resp = send("GET", "/internal/products/999999999/index-doc", null, null);
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.json().get("code").asInt()).isEqualTo(30007);

        long categoryId = createLeafCategory();
        long productId = createProduct(categoryId, 500);
        send("DELETE", "/api/v1/admin/products/" + productId, null, operatorToken);
        assertThat(send("GET", "/internal/products/" + productId + "/index-doc", null, null)
                .json().get("code").asInt()).isEqualTo(30007);
    }

    @Test
    @DisplayName("索引文档接口：无品牌、无上架时间的商品也能正常出参（不因空集合查询 500）")
    void indexDocToleratesMissingBrandAndOnSaleTime() throws Exception {
        long categoryId = createLeafCategory();
        long productId = createProduct(categoryId, 100);

        Resp resp = send("GET", "/internal/products/" + productId + "/index-doc", null, null);
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.json().get("code").asInt()).isZero();
        JsonNode doc = resp.json().get("data");
        assertThat(doc.get("brandId").isNull()).isTrue();
        assertThat(doc.get("brandName").isNull()).isTrue();
        assertThat(doc.get("status").asString()).isEqualTo("OFF_SALE");
        assertThat(doc.get("onSaleTime").isNull()).isTrue();
    }

    @Test
    @DisplayName("索引文档分页：含已下架商品，供全量重建使用")
    void indexDocPageIncludesOffSaleProducts() throws Exception {
        long categoryId = createLeafCategory();
        long productId = createProduct(categoryId, 100);

        JsonNode page = send("GET", "/internal/products/index-docs?page=1&size=500", null, null).json().get("data");
        assertThat(page.get("total").asLong()).isPositive();
        boolean found = false;
        for (JsonNode doc : page.get("list")) {
            if (doc.get("id").asLong() == productId) {
                found = true;
                assertThat(doc.get("status").asString()).isEqualTo("OFF_SALE");
            }
        }
        assertThat(found).as("全量拉取必须包含下架商品（由搜索侧过滤，保证重新上架立即生效）").isTrue();
    }
}
