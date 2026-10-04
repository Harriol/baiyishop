package com.harriol.baiyishop.inventory;

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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存服务端到端验证（REQ-501 ~ REQ-503、REQ-505）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryApiTests {

    private static final AtomicLong SKU_SEQ = new AtomicLong(System.currentTimeMillis() % 1000000);

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

    private long newSkuId() {
        return SKU_SEQ.incrementAndGet();
    }

    private static String orderNo() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 建一条有货的 SKU（跳过预警阈值以下的干扰） */
    private long skuWithStock(int stock) throws Exception {
        long skuId = newSkuId();
        Resp resp = send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust",
                "{\"delta\":" + stock + ",\"reason\":\"初始化测试库存\"}", operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        return skuId;
    }

    private JsonNode inventoryOf(long skuId) throws Exception {
        return send("GET", "/api/v1/admin/inventory?page=1&size=10&skuId=" + skuId, null, operatorToken)
                .json().get("data").get("list").get(0);
    }

    private Resp lock(long skuId, int qty, String no) throws Exception {
        return send("POST", "/internal/inventory/lock",
                "{\"orderNo\":\"" + no + "\",\"items\":[{\"skuId\":" + skuId + ",\"quantity\":" + qty + "}]}", null);
    }

    @Test
    @DisplayName("后台补货：可售增加并写流水（含变更前后值、原因、操作人）")
    void adjustWritesFlow() throws Exception {
        long skuId = newSkuId();
        Resp adjusted = send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust",
                "{\"delta\":100,\"reason\":\"到货补货\"}", operatorToken);
        assertThat(adjusted.status()).isEqualTo(200);
        assertThat(adjusted.json().get("data").get("available").asInt()).isEqualTo(100);

        JsonNode flows = send("GET", "/api/v1/admin/inventory/flows?page=1&size=10&skuId=" + skuId, null, operatorToken)
                .json().get("data").get("list");
        assertThat(flows.size()).isEqualTo(1);
        JsonNode flow = flows.get(0);
        assertThat(flow.get("type").asString()).isEqualTo("ADJUST");
        assertThat(flow.get("quantity").asInt()).isEqualTo(100);
        assertThat(flow.get("beforeAvailable").asInt()).isEqualTo(0);
        assertThat(flow.get("afterAvailable").asInt()).isEqualTo(100);
        assertThat(flow.get("reason").asString()).isEqualTo("到货补货");
        assertThat(flow.get("operatorType").asString()).isEqualTo("ADMIN");
        assertThat(flow.get("operatorId").asLong()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("调整数量非法：delta=0 或减成负数都返回 40003")
    void invalidAdjustmentRejected() throws Exception {
        long skuId = skuWithStock(10);
        assertThat(send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust",
                "{\"delta\":0,\"reason\":\"无效\"}", operatorToken).json().get("code").asInt()).isEqualTo(40003);
        assertThat(send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust",
                "{\"delta\":-100,\"reason\":\"超减\"}", operatorToken).json().get("code").asInt()).isEqualTo(40003);
        assertThat(inventoryOf(skuId).get("available").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("下单锁定：可售减少、锁定增加，流水类型为 LOCK")
    void lockMovesStockToLocked() throws Exception {
        long skuId = skuWithStock(50);
        String no = orderNo();
        assertThat(lock(skuId, 20, no).json().get("code").asInt()).isZero();

        JsonNode inv = inventoryOf(skuId);
        assertThat(inv.get("available").asInt()).isEqualTo(30);
        assertThat(inv.get("locked").asInt()).isEqualTo(20);

        JsonNode flow = send("GET", "/api/v1/admin/inventory/flows?page=1&size=10&skuId=" + skuId + "&type=LOCK",
                null, operatorToken).json().get("data").get("list").get(0);
        assertThat(flow.get("beforeAvailable").asInt()).isEqualTo(50);
        assertThat(flow.get("afterAvailable").asInt()).isEqualTo(30);
        assertThat(flow.get("beforeLocked").asInt()).isEqualTo(0);
        assertThat(flow.get("afterLocked").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("锁定幂等：同一订单重复调用只生效一次")
    void lockIsIdempotent() throws Exception {
        long skuId = skuWithStock(50);
        String no = orderNo();
        lock(skuId, 20, no);
        Resp again = lock(skuId, 20, no);

        assertThat(again.json().get("code").asInt()).isZero();
        assertThat(again.json().get("data").get("alreadyProcessed").asBoolean()).isTrue();
        JsonNode inv = inventoryOf(skuId);
        assertThat(inv.get("available").asInt()).isEqualTo(30);
        assertThat(inv.get("locked").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("库存不足返回 40001，且不会产生部分锁定")
    void insufficientStockRollsBackAll() throws Exception {
        long enough = skuWithStock(100);
        long notEnough = skuWithStock(1);
        String no = orderNo();

        Resp resp = send("POST", "/internal/inventory/lock",
                "{\"orderNo\":\"" + no + "\",\"items\":["
                        + "{\"skuId\":" + enough + ",\"quantity\":5},"
                        + "{\"skuId\":" + notEnough + ",\"quantity\":99}]}", null);
        assertThat(resp.json().get("code").asInt()).isEqualTo(40001);
        assertThat(resp.json().get("message").asString()).contains(String.valueOf(notEnough));

        // 关键：第一个 SKU 也不能被锁住
        assertThat(inventoryOf(enough).get("available").asInt()).isEqualTo(100);
        assertThat(inventoryOf(enough).get("locked").asInt()).isZero();
        assertThat(inventoryOf(notEnough).get("available").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("支付扣减：锁定量减少（总库存真正出库）")
    void deductReducesLocked() throws Exception {
        long skuId = skuWithStock(30);
        String no = orderNo();
        lock(skuId, 10, no);

        assertThat(send("POST", "/internal/inventory/deduct",
                "{\"orderNo\":\"" + no + "\",\"items\":[{\"skuId\":" + skuId + ",\"quantity\":10}]}", null)
                .json().get("code").asInt()).isZero();

        JsonNode inv = inventoryOf(skuId);
        assertThat(inv.get("available").asInt()).isEqualTo(20);
        assertThat(inv.get("locked").asInt()).isZero();
    }

    @Test
    @DisplayName("取消释放：锁定回到可售，且重复释放不会重复加回")
    void releaseIsIdempotent() throws Exception {
        long skuId = skuWithStock(30);
        String no = orderNo();
        lock(skuId, 10, no);

        assertThat(send("POST", "/internal/inventory/release",
                "{\"orderNo\":\"" + no + "\",\"items\":[{\"skuId\":" + skuId + ",\"quantity\":10}]}", null)
                .json().get("code").asInt()).isZero();
        // 再释放一次
        assertThat(send("POST", "/internal/inventory/release",
                "{\"orderNo\":\"" + no + "\",\"items\":[{\"skuId\":" + skuId + ",\"quantity\":10}]}", null)
                .json().get("code").asInt()).isZero();

        JsonNode inv = inventoryOf(skuId);
        assertThat(inv.get("available").asInt()).isEqualTo(30);
        assertThat(inv.get("locked").asInt()).isZero();
    }

    @Test
    @DisplayName("库存预警：低于阈值生成 OPEN，补货后自动关闭")
    void alertLifecycle() throws Exception {
        long skuId = newSkuId();
        // 先给 20，再减到 5（阈值默认 10）
        send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust", "{\"delta\":20,\"reason\":\"备货\"}", operatorToken);
        send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust", "{\"delta\":-15,\"reason\":\"盘点修正\"}", operatorToken);

        JsonNode openAlerts = send("GET", "/api/v1/admin/inventory/alerts?status=OPEN", null, operatorToken)
                .json().get("data");
        boolean hasOpen = false;
        for (JsonNode node : openAlerts) {
            if (node.get("skuId").asLong() == skuId) {
                hasOpen = true;
                assertThat(node.get("currentStock").asInt()).isEqualTo(5);
            }
        }
        assertThat(hasOpen).isTrue();
        assertThat(inventoryOf(skuId).get("alerting").asBoolean()).isTrue();

        // 补货回阈值以上 → 自动关闭
        send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust", "{\"delta\":50,\"reason\":\"到货补货\"}", operatorToken);
        JsonNode openAfter = send("GET", "/api/v1/admin/inventory/alerts?status=OPEN", null, operatorToken).json().get("data");
        for (JsonNode node : openAfter) {
            assertThat(node.get("skuId").asLong()).isNotEqualTo(skuId);
        }
        assertThat(inventoryOf(skuId).get("alerting").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("只读库存：有记录返回值，无记录返回 null（不伪造 0）")
    void publicAvailable() throws Exception {
        long skuId = skuWithStock(7);
        assertThat(send("GET", "/api/v1/inventory/skus/" + skuId + "/available", null, null)
                .json().get("data").get("available").asInt()).isEqualTo(7);

        long unknownSku = newSkuId();
        Resp unknown = send("GET", "/api/v1/inventory/skus/" + unknownSku + "/available", null, null);
        assertThat(unknown.json().get("code").asInt()).isZero();
        assertThat(unknown.json().get("data").get("available").isNull()).isTrue();
    }

    @Test
    @DisplayName("角色校验：客服 403、无令牌 401、内部接口不校验令牌")
    void roleEnforcement() throws Exception {
        assertThat(send("GET", "/api/v1/admin/inventory", null, null).status()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/admin/inventory", null, serviceToken).status()).isEqualTo(403);
        assertThat(send("PUT", "/api/v1/admin/inventory/1/adjust", "{\"delta\":1,\"reason\":\"x\"}", serviceToken)
                .status()).isEqualTo(403);
        // 内部接口：网关负责挡外部流量，服务自身不要求令牌
        assertThat(send("GET", "/internal/inventory/skus/" + skuWithStock(3), null, null).status()).isEqualTo(200);
    }
}