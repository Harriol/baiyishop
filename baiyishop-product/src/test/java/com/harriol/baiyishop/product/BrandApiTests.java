package com.harriol.baiyishop.product;

import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * 品牌管理端到端验证（REQ-202）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandApiTests {

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private long createBrand(String brandName) throws Exception {
        Resp resp = send("POST", "/api/v1/admin/brands",
                "{\"name\":\"" + brandName + "\",\"description\":\"测试品牌\",\"sort\":5}", operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("id").asLong();
    }

    private boolean publicContains(String brandName) throws Exception {
        JsonNode list = send("GET", "/api/v1/brands", null, null).json().get("data");
        for (JsonNode node : list) {
            if (node.get("name").asString().equals(brandName)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("新增品牌后前台可见")
    void createBrandVisibleOnFrontend() throws Exception {
        String brandName = name("品牌");
        long id = createBrand(brandName);
        assertThat(id).isPositive();
        assertThat(publicContains(brandName)).isTrue();
    }

    @Test
    @DisplayName("品牌名重复被拒，提示可读")
    void duplicateNameRejected() throws Exception {
        String brandName = name("重名");
        createBrand(brandName);
        Resp dup = send("POST", "/api/v1/admin/brands", "{\"name\":\"" + brandName + "\"}", operatorToken);
        assertThat(dup.status()).isEqualTo(400);
        assertThat(dup.json().get("code").asInt()).isEqualTo(10001);
        assertThat(dup.json().get("message").asString()).contains("已存在");
    }

    @Test
    @DisplayName("停用后前台不可见，后台仍可见且可按状态筛选")
    void disabledBrandHiddenFromFrontend() throws Exception {
        String brandName = name("停用");
        long id = createBrand(brandName);
        assertThat(send("PUT", "/api/v1/admin/brands/" + id + "/enabled?enabled=false", null, operatorToken)
                .json().get("code").asInt()).isZero();

        assertThat(publicContains(brandName)).isFalse();

        Resp filtered = send("GET", "/api/v1/admin/brands?page=1&size=50&keyword=" + brandName + "&enabled=false", null, operatorToken);
        assertThat(filtered.json().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(filtered.json().get("data").get("list").get(0).get("enabled").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("被商品引用的品牌不可删除（30006），移除引用后可删")
    void deleteInUseRejected() throws Exception {
        String brandName = name("被引用");
        long id = createBrand(brandName);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO product (name, category_id, brand_id, main_image, status, min_price, sales, deleted)
                VALUES (?, ?, ?, 'https://minio/x.jpg', 'OFF_SALE', 100, 0, 0)
                """, name("商品"), categoryId, id);

        Resp denied = send("DELETE", "/api/v1/admin/brands/" + id, null, operatorToken);
        assertThat(denied.json().get("code").asInt()).isEqualTo(30006);

        jdbcTemplate.update("DELETE FROM product WHERE brand_id = ?", id);
        assertThat(send("DELETE", "/api/v1/admin/brands/" + id, null, operatorToken)
                .json().get("code").asInt()).isZero();
        assertThat(publicContains(brandName)).isFalse();
    }

    @Test
    @DisplayName("品牌不存在返回 30005")
    void notFoundRejected() throws Exception {
        assertThat(send("DELETE", "/api/v1/admin/brands/99999999", null, operatorToken)
                .json().get("code").asInt()).isEqualTo(30005);
        assertThat(send("PUT", "/api/v1/admin/brands/99999999/enabled?enabled=false", null, operatorToken)
                .json().get("code").asInt()).isEqualTo(30005);
    }

    @Test
    @DisplayName("后台分页列表结构与名称过滤")
    void adminPageShape() throws Exception {
        String brandName = name("分页");
        createBrand(brandName);

        Resp resp = send("GET", "/api/v1/admin/brands?page=1&size=5&keyword=" + brandName, null, operatorToken);
        JsonNode data = resp.json().get("data");
        assertThat(data.get("page").asLong()).isEqualTo(1);
        assertThat(data.get("size").asLong()).isEqualTo(5);
        assertThat(data.get("total").asLong()).isEqualTo(1);
        assertThat(data.get("list").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("角色校验：客服 403、无令牌 401、前台品牌列表公开")
    void roleEnforcement() throws Exception {
        assertThat(send("GET", "/api/v1/admin/brands", null, null).status()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/admin/brands", null, serviceToken).status()).isEqualTo(403);
        assertThat(send("POST", "/api/v1/admin/brands", "{\"name\":\"x\"}", serviceToken).status()).isEqualTo(403);
        assertThat(send("GET", "/api/v1/brands", null, null).status()).isEqualTo(200);
    }
}