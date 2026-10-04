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
 * 秒杀库存池端到端验证（REQ-504、REQ-905）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeckillPoolApiTests {

    private static final AtomicLong SKU_SEQ = new AtomicLong(System.currentTimeMillis() % 1000000 + 500000);
    private static final AtomicLong SKU_ID_SEQ = new AtomicLong(System.currentTimeMillis() % 1000000 + 700000);

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

    private static String uniq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private long newSku(int stock) throws Exception {
        long skuId = SKU_SEQ.incrementAndGet();
        send("PUT", "/api/v1/admin/inventory/" + skuId + "/adjust",
                "{\"delta\":" + stock + ",\"reason\":\"秒杀测试备货\"}", operatorToken);
        return skuId;
    }

    private long newActivitySku() {
        return SKU_ID_SEQ.incrementAndGet();
    }

    private int availableOf(long skuId) throws Exception {
        return send("GET", "/api/v1/inventory/skus/" + skuId + "/available", null, null)
                .json().get("data").get("available").asInt();
    }

    private Resp allocate(long activitySkuId, long skuId, int quantity, String batchNo) throws Exception {
        return send("POST", "/internal/inventory/seckill/allocate",
                "{\"activityId\":" + (activitySkuId + 1) + ",\"activitySkuId\":" + activitySkuId
                        + ",\"skuId\":" + skuId + ",\"quantity\":" + quantity + ",\"batchNo\":\"" + batchNo + "\"}", null);
    }

    @Test
    @DisplayName("划拨：普通库存立即扣减，秒杀池入账，两类流水都写")
    void allocateTakesFromNormalStock() throws Exception {
        long skuId = newSku(100);
        long activitySkuId = newActivitySku();

        Resp resp = allocate(activitySkuId, skuId, 30, uniq());
        assertThat(resp.json().get("code").asInt()).isZero();
        JsonNode pool = resp.json().get("data");
        assertThat(pool.get("total").asInt()).isEqualTo(30);
        assertThat(pool.get("remaining").asInt()).isEqualTo(30);
        assertThat(pool.get("sold").asInt()).isZero();

        // 普通库存立即扣减（REQ-504）
        assertThat(availableOf(skuId)).isEqualTo(70);

        JsonNode flows = send("GET", "/api/v1/admin/inventory/flows?page=1&size=10&skuId=" + skuId + "&type=ALLOCATE",
                null, operatorToken).json().get("data").get("list");
        assertThat(flows.size()).isEqualTo(1);
        assertThat(flows.get(0).get("beforeAvailable").asInt()).isEqualTo(100);
        assertThat(flows.get(0).get("afterAvailable").asInt()).isEqualTo(70);
    }

    @Test
    @DisplayName("划拨超过可售库存返回 40005，且普通库存不被改动")
    void allocateBeyondStockRejected() throws Exception {
        long skuId = newSku(10);
        Resp resp = allocate(newActivitySku(), skuId, 50, uniq());
        assertThat(resp.json().get("code").asInt()).isEqualTo(40005);
        assertThat(availableOf(skuId)).isEqualTo(10);
    }

    @Test
    @DisplayName("划拨幂等：同一 batchNo 重复调用不会重复扣减")
    void allocateIsIdempotent() throws Exception {
        long skuId = newSku(100);
        long activitySkuId = newActivitySku();
        String batchNo = uniq();

        allocate(activitySkuId, skuId, 20, batchNo);
        allocate(activitySkuId, skuId, 20, batchNo);

        assertThat(availableOf(skuId)).isEqualTo(80);
        assertThat(send("GET", "/internal/inventory/seckill/pool/" + activitySkuId, null, null)
                .json().get("data").get("remaining").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("秒杀成交：池内剩余减少、已售增加，且 remaining + sold = total")
    void deductFromPool() throws Exception {
        long skuId = newSku(100);
        long activitySkuId = newActivitySku();
        allocate(activitySkuId, skuId, 40, uniq());

        String orderNo = "S" + uniq();
        Resp deducted = send("POST", "/internal/inventory/seckill/deduct?activitySkuId=" + activitySkuId
                + "&quantity=5&orderNo=" + orderNo, "", null);
        assertThat(deducted.json().get("code").asInt()).isZero();
        JsonNode pool = deducted.json().get("data");
        assertThat(pool.get("remaining").asInt()).isEqualTo(35);
        assertThat(pool.get("sold").asInt()).isEqualTo(5);
        assertThat(pool.get("remaining").asInt() + pool.get("sold").asInt()).isEqualTo(pool.get("total").asInt());

        // 同一订单重复扣减：幂等
        send("POST", "/internal/inventory/seckill/deduct?activitySkuId=" + activitySkuId
                + "&quantity=5&orderNo=" + orderNo, "", null);
        assertThat(send("GET", "/internal/inventory/seckill/pool/" + activitySkuId, null, null)
                .json().get("data").get("sold").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("秒杀订单取消：回滚到秒杀池（用户可再次抢购），且幂等")
    void rollbackToPool() throws Exception {
        long skuId = newSku(100);
        long activitySkuId = newActivitySku();
        allocate(activitySkuId, skuId, 20, uniq());
        String orderNo = "S" + uniq();
        send("POST", "/internal/inventory/seckill/deduct?activitySkuId=" + activitySkuId
                + "&quantity=3&orderNo=" + orderNo, "", null);

        String body = "{\"activitySkuId\":" + activitySkuId + ",\"mode\":\"ROLLBACK\",\"quantity\":3,"
                + "\"orderNo\":\"" + orderNo + "\",\"batchNo\":\"" + uniq() + "\"}";
        Resp rolled = send("POST", "/internal/inventory/seckill/return", body, null);
        assertThat(rolled.json().get("data").get("remaining").asInt()).isEqualTo(20);
        assertThat(rolled.json().get("data").get("sold").asInt()).isZero();

        // 重复回滚不重复加回
        send("POST", "/internal/inventory/seckill/return", body, null);
        assertThat(send("GET", "/internal/inventory/seckill/pool/" + activitySkuId, null, null)
                .json().get("data").get("remaining").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("活动结束：未售出自动回补普通库存，remaining 归零且幂等")
    void unsoldReturnsToNormalStock() throws Exception {
        long skuId = newSku(100);
        long activitySkuId = newActivitySku();
        allocate(activitySkuId, skuId, 40, uniq());
        assertThat(availableOf(skuId)).isEqualTo(60);

        String body = "{\"activitySkuId\":" + activitySkuId + ",\"mode\":\"UNSOLD\",\"batchNo\":\"" + uniq() + "\"}";
        Resp returned = send("POST", "/internal/inventory/seckill/return", body, null);
        assertThat(returned.json().get("data").get("remaining").asInt()).isZero();
        assertThat(availableOf(skuId)).isEqualTo(100);

        // 再回补一次：池内已无剩余，不产生副作用
        send("POST", "/internal/inventory/seckill/return", body, null);
        assertThat(availableOf(skuId)).isEqualTo(100);
    }

    @Test
    @DisplayName("秒杀池不存在时回补返回 40004")
    void poolNotFound() throws Exception {
        Resp resp = send("POST", "/internal/inventory/seckill/return",
                "{\"activitySkuId\":" + newActivitySku() + ",\"mode\":\"UNSOLD\",\"batchNo\":\"" + uniq() + "\"}", null);
        assertThat(resp.json().get("code").asInt()).isEqualTo(40004);
    }
}