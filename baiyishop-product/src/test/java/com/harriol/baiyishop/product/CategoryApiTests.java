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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分类管理端到端验证（REQ-201、REQ-205）。
 * <p>用真实 HTTP 打接口；管理员令牌直接由 JwtTokenProvider 签发，
 * 便于单独验证角色校验而不依赖管理员表。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryApiTests {

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

    private long createCategory(Long parentId, String name) throws Exception {
        String body = "{\"parentId\":" + (parentId == null ? 0 : parentId) + ",\"name\":\"" + name + "\",\"sort\":1}";
        Resp resp = send("POST", "/api/v1/admin/categories", body, operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("id").asLong();
    }

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    /** 在树上按 id 递归查找节点 */
    private JsonNode findNode(JsonNode nodes, long id) {
        for (JsonNode node : nodes) {
            if (node.get("id").asLong() == id) {
                return node;
            }
            JsonNode found = findNode(node.get("children"), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("可建三级分类，path 与 level 正确")
    void createThreeLevelsWithMaterializedPath() throws Exception {
        long l1 = createCategory(null, name("一级"));
        long l2 = createCategory(l1, name("二级"));
        long l3 = createCategory(l2, name("三级"));

        JsonNode tree = send("GET", "/api/v1/admin/categories", null, operatorToken).json().get("data");
        JsonNode n1 = findNode(tree, l1);
        JsonNode n2 = findNode(tree, l2);
        JsonNode n3 = findNode(tree, l3);

        assertThat(n1.get("level").asInt()).isEqualTo(1);
        assertThat(n2.get("level").asInt()).isEqualTo(2);
        assertThat(n3.get("level").asInt()).isEqualTo(3);
        assertThat(n3.get("path").asString()).isEqualTo("/" + l1 + "/" + l2 + "/" + l3 + "/");
    }

    @Test
    @DisplayName("第 4 级被拒，返回 30004")
    void fourthLevelRejected() throws Exception {
        long l1 = createCategory(null, name("L1"));
        long l2 = createCategory(l1, name("L2"));
        long l3 = createCategory(l2, name("L3"));

        Resp resp = send("POST", "/api/v1/admin/categories",
                "{\"parentId\":" + l3 + ",\"name\":\"第四级\"}", operatorToken);
        assertThat(resp.json().get("code").asInt()).isEqualTo(30004);
    }

    @Test
    @DisplayName("删除有子分类的分类返回 30002")
    void deleteWithChildrenRejected() throws Exception {
        long parent = createCategory(null, name("父"));
        createCategory(parent, name("子"));

        Resp resp = send("DELETE", "/api/v1/admin/categories/" + parent, null, operatorToken);
        assertThat(resp.json().get("code").asInt()).isEqualTo(30002);
    }

    @Test
    @DisplayName("删除被商品引用的分类返回 30003")
    void deleteInUseRejected() throws Exception {
        long categoryId = createCategory(null, name("被引用"));
        jdbcTemplate.update("""
                INSERT INTO product (name, category_id, brand_id, main_image, status, min_price, sales, deleted)
                VALUES (?, ?, NULL, 'https://minio/x.jpg', 'OFF_SALE', 100, 0, 0)
                """, name("测试商品"), categoryId);

        Resp resp = send("DELETE", "/api/v1/admin/categories/" + categoryId, null, operatorToken);
        assertThat(resp.json().get("code").asInt()).isEqualTo(30003);

        jdbcTemplate.update("DELETE FROM product WHERE category_id = ?", categoryId);
        assertThat(send("DELETE", "/api/v1/admin/categories/" + categoryId, null, operatorToken)
                .json().get("code").asInt()).isZero();
    }

    @Test
    @DisplayName("隐藏分类：后台树可见，前台树不含，且其子分类也不出现在前台树")
    void hiddenCategoryInvisibleToFrontend() throws Exception {
        long hidden = createCategory(null, name("隐藏"));
        long child = createCategory(hidden, name("隐藏下"));

        Resp hiddenResp = send("PUT", "/api/v1/admin/categories/" + hidden + "/visible?visible=false", null, operatorToken);
        assertThat(hiddenResp.json().get("code").asInt()).isZero();

        JsonNode adminTree = send("GET", "/api/v1/admin/categories", null, operatorToken).json().get("data");
        assertThat(findNode(adminTree, hidden)).isNotNull();

        JsonNode publicTree = send("GET", "/api/v1/categories/tree", null, null).json().get("data");
        assertThat(findNode(publicTree, hidden)).isNull();
        assertThat(findNode(publicTree, child)).isNull();
    }

    @Test
    @DisplayName("调整父级后，自身与全部后代的 path、level 一并重算")
    void movingSubtreeRewritesPaths() throws Exception {
        long a = createCategory(null, name("甲"));
        long b = createCategory(a, name("乙"));
        long leaf = createCategory(b, name("叶"));

        // 把「乙」整棵子树挪到「丙」下面
        long c = createCategory(null, name("丙"));
        Resp moved = send("PUT", "/api/v1/admin/categories/" + b,
                "{\"parentId\":" + c + ",\"name\":\"乙\"}", operatorToken);
        assertThat(moved.json().get("code").asInt()).isZero();
        assertThat(moved.json().get("data").get("path").asString()).isEqualTo("/" + c + "/" + b + "/");
        assertThat(moved.json().get("data").get("level").asInt()).isEqualTo(2);

        JsonNode tree = send("GET", "/api/v1/admin/categories", null, operatorToken).json().get("data");
        JsonNode movedLeaf = findNode(tree, leaf);
        assertThat(movedLeaf.get("level").asInt()).isEqualTo(3);
        assertThat(movedLeaf.get("path").asString()).isEqualTo("/" + c + "/" + b + "/" + leaf + "/");
    }

    @Test
    @DisplayName("不能把分类挂到自己的子分类下")
    void cannotMoveUnderOwnDescendant() throws Exception {
        long parent = createCategory(null, name("父2"));
        long child = createCategory(parent, name("子2"));

        Resp resp = send("PUT", "/api/v1/admin/categories/" + parent,
                "{\"parentId\":" + child + ",\"name\":\"父2\"}", operatorToken);
        assertThat(resp.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("角色校验：客服不可访问分类管理（403），无令牌 401，前台树公开")
    void roleEnforcement() throws Exception {
        assertThat(send("GET", "/api/v1/admin/categories", null, null).status()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/admin/categories", null, serviceToken).status()).isEqualTo(403);
        assertThat(send("GET", "/api/v1/admin/categories", null, serviceToken).json().get("code").asInt()).isEqualTo(10003);

        Resp publicTree = send("GET", "/api/v1/categories/tree", null, null);
        assertThat(publicTree.status()).isEqualTo(200);
        assertThat(publicTree.json().get("code").asInt()).isZero();
    }
}