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
 * 商品参数模板端到端验证（REQ-204）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParamApiTests {

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

    private long createTemplate(String templateName) throws Exception {
        return send("POST", "/api/v1/admin/params/templates",
                "{\"name\":\"" + templateName + "\",\"sort\":1}", operatorToken)
                .json().get("data").get("id").asLong();
    }

    private long createItem(long templateId, String itemName, int sort) throws Exception {
        return send("POST", "/api/v1/admin/params/items",
                "{\"templateId\":" + templateId + ",\"name\":\"" + itemName + "\",\"sort\":" + sort + "}", operatorToken)
                .json().get("data").get("id").asLong();
    }

    private long createLeafCategory() throws Exception {
        long root = send("POST", "/api/v1/admin/categories",
                "{\"parentId\":0,\"name\":\"" + name("根") + "\"}", operatorToken).json().get("data").get("id").asLong();
        return send("POST", "/api/v1/admin/categories",
                "{\"parentId\":" + root + ",\"name\":\"" + name("叶") + "\"}", operatorToken)
                .json().get("data").get("id").asLong();
    }

    private long createProductWithParams(long categoryId, String paramsJson) throws Exception {
        String body = "{\"name\":\"" + name("带参商品") + "\",\"categoryId\":" + categoryId + ","
                + "\"mainImage\":\"https://minio/main.jpg\",\"onSale\":true,"
                + "\"params\":" + paramsJson + ","
                + "\"skus\":[{\"specName\":\"默认规格\",\"price\":1000}]}";
        Resp resp = send("POST", "/api/v1/admin/products", body, operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("id").asLong();
    }

    @Test
    @DisplayName("模板列表返回各自参数项，删除有参数项的模板被拒 30013")
    void templateWithItemsCannotBeDeleted() throws Exception {
        long templateId = createTemplate(name("模板"));
        String itemName = name("参数项");
        createItem(templateId, itemName, 1);

        JsonNode templates = send("GET", "/api/v1/admin/params/templates", null, operatorToken).json().get("data");
        JsonNode found = null;
        for (JsonNode node : templates) {
            if (node.get("id").asLong() == templateId) {
                found = node;
            }
        }
        assertThat(found).isNotNull();
        assertThat(found.get("items").size()).isEqualTo(1);
        assertThat(found.get("items").get(0).get("name").asString()).isEqualTo(itemName);

        Resp denied = send("DELETE", "/api/v1/admin/params/templates/" + templateId, null, operatorToken);
        assertThat(denied.json().get("code").asInt()).isEqualTo(30013);
    }

    @Test
    @DisplayName("商品保存参数值，详情按参数项排序返回")
    void productStoresAndReturnsParams() throws Exception {
        long templateId = createTemplate(name("服装模板"));
        long material = createItem(templateId, "材质", 2);
        long origin = createItem(templateId, "产地", 1);

        long productId = createProductWithParams(createLeafCategory(),
                "[{\"paramItemId\":" + material + ",\"value\":\"100% 棉\"},{\"paramItemId\":" + origin + ",\"value\":\"浙江\"}]");

        JsonNode params = send("GET", "/api/v1/products/" + productId, null, null).json().get("data").get("params");
        assertThat(params.size()).isEqualTo(2);
        // 按参数项 sort 排序：产地(1) 在前，材质(2) 在后
        assertThat(params.get(0).get("name").asString()).isEqualTo("产地");
        assertThat(params.get(0).get("value").asString()).isEqualTo("浙江");
        assertThat(params.get(1).get("name").asString()).isEqualTo("材质");
    }

    @Test
    @DisplayName("参数项跨模板提交被拒 30009；参数项不存在返回 30011")
    void crossTemplateAndMissingItemRejected() throws Exception {
        long templateA = createTemplate(name("模板A"));
        long templateB = createTemplate(name("模板B"));
        long itemA = createItem(templateA, name("项A"), 1);
        long itemB = createItem(templateB, name("项B"), 1);
        long categoryId = createLeafCategory();

        Resp cross = send("POST", "/api/v1/admin/products",
                "{\"name\":\"" + name("跨模板") + "\",\"categoryId\":" + categoryId + ",\"mainImage\":\"m.jpg\","
                        + "\"params\":[{\"paramItemId\":" + itemA + ",\"value\":\"a\"},{\"paramItemId\":" + itemB + ",\"value\":\"b\"}],"
                        + "\"skus\":[{\"price\":100}]}", operatorToken);
        assertThat(cross.json().get("code").asInt()).isEqualTo(30009);

        Resp missing = send("POST", "/api/v1/admin/products",
                "{\"name\":\"" + name("缺项") + "\",\"categoryId\":" + categoryId + ",\"mainImage\":\"m.jpg\","
                        + "\"params\":[{\"paramItemId\":99999999,\"value\":\"x\"}],"
                        + "\"skus\":[{\"price\":100}]}", operatorToken);
        assertThat(missing.json().get("code").asInt()).isEqualTo(30011);
    }

    @Test
    @DisplayName("参数项改名后，商品详情里的参数名同步变化（说明未冗余名称）")
    void renamingItemReflectsOnProduct() throws Exception {
        long templateId = createTemplate(name("改名模板"));
        long itemId = createItem(templateId, name("旧名"), 1);
        long productId = createProductWithParams(createLeafCategory(),
                "[{\"paramItemId\":" + itemId + ",\"value\":\"纯棉\"}]");

        Resp renamed = send("PUT", "/api/v1/admin/params/items/" + itemId,
                "{\"name\":\"面料成分\",\"sort\":1}", operatorToken);
        assertThat(renamed.json().get("code").asInt()).isZero();

        JsonNode params = send("GET", "/api/v1/products/" + productId, null, null).json().get("data").get("params");
        assertThat(params.get(0).get("name").asString()).isEqualTo("面料成分");
        assertThat(params.get(0).get("value").asString()).isEqualTo("纯棉");
    }

    @Test
    @DisplayName("参数项被商品使用时不可删除（30012），解除引用后可删且详情不再展示")
    void itemInUseCannotBeDeleted() throws Exception {
        long templateId = createTemplate(name("占用模板"));
        long itemId = createItem(templateId, name("被占用"), 1);
        long productId = createProductWithParams(createLeafCategory(),
                "[{\"paramItemId\":" + itemId + ",\"value\":\"v\"}]");

        Resp denied = send("DELETE", "/api/v1/admin/params/items/" + itemId, null, operatorToken);
        assertThat(denied.json().get("code").asInt()).isEqualTo(30012);

        // 商品改成不带参数后再删
        assertThat(send("PUT", "/api/v1/admin/products/" + productId,
                "{\"name\":\"" + name("无参") + "\",\"categoryId\":" + createLeafCategory() + ",\"mainImage\":\"m.jpg\","
                        + "\"params\":[],\"skus\":[{\"price\":100}]}", operatorToken)
                .json().get("code").asInt()).isZero();

        assertThat(send("DELETE", "/api/v1/admin/params/items/" + itemId, null, operatorToken)
                .json().get("code").asInt()).isZero();
        assertThat(send("GET", "/api/v1/products/" + productId, null, null).json().get("data").get("params").size()).isZero();
    }

    @Test
    @DisplayName("参数项不存在返回 30011；模板不存在返回 30010")
    void notFoundCodes() throws Exception {
        assertThat(send("PUT", "/api/v1/admin/params/items/99999999", "{\"name\":\"x\"}", operatorToken)
                .json().get("code").asInt()).isEqualTo(30011);
        assertThat(send("GET", "/api/v1/admin/params/templates/99999999/items", null, operatorToken)
                .json().get("code").asInt()).isEqualTo(30010);
    }

    @Test
    @DisplayName("角色校验：客服 403、无令牌 401")
    void roleEnforcement() throws Exception {
        assertThat(send("GET", "/api/v1/admin/params/templates", null, null).status()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/admin/params/templates", null, serviceToken).status()).isEqualTo(403);
        assertThat(send("POST", "/api/v1/admin/params/templates", "{\"name\":\"x\"}", serviceToken).status()).isEqualTo(403);
    }
}