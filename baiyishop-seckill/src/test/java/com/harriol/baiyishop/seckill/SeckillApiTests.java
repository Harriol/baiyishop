package com.harriol.baiyishop.seckill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.seckill.client.InventoryClient;
import com.harriol.baiyishop.seckill.client.OrderClient;
import com.harriol.baiyishop.seckill.client.ProductClient;
import com.harriol.baiyishop.seckill.dto.SeckillPoolView;
import com.harriol.baiyishop.seckill.dto.AllocateStockRequest;
import com.harriol.baiyishop.seckill.dto.SeckillResultRequest;
import com.harriol.baiyishop.seckill.dto.SkuSnapshot;
import com.harriol.baiyishop.seckill.entity.MqOutbox;
import com.harriol.baiyishop.seckill.entity.SeckillRecord;
import com.harriol.baiyishop.seckill.mapper.MqOutboxMapper;
import com.harriol.baiyishop.seckill.mapper.SeckillActivitySkuMapper;
import com.harriol.baiyishop.seckill.mapper.SeckillRecordMapper;
import com.harriol.baiyishop.seckill.redis.SeckillStockRedis;
import com.harriol.baiyishop.seckill.service.SeckillResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 秒杀端到端验证（REQ-901 ~ REQ-906）。
 * <p>Redis 用真实实例（预扣脚本本身就是被测对象），inventory / product / order 用替身顶掉，
 * 真跨服务链路由冒烟测试覆盖。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeckillApiTests {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicLong USER_SEQ = new AtomicLong(4_100_000_000L + System.currentTimeMillis() % 100_000_000L);
    private static final AtomicLong SKU_SEQ = new AtomicLong(7_000_000L + System.currentTimeMillis() % 1_000_000L);

    private static final String ADMIN_TOKEN_ROLE = "OPERATOR";

    @TestConfiguration
    static class StubClients {
        @Bean
        @Primary
        InventoryClient inventoryClient() {
            return Mockito.mock(InventoryClient.class);
        }

        @Bean
        @Primary
        ProductClient productClient() {
            return Mockito.mock(ProductClient.class);
        }

        @Bean
        @Primary
        OrderClient orderClient() {
            return Mockito.mock(OrderClient.class);
        }
    }

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private ProductClient productClient;

    @Autowired
    private OrderClient orderClient;

    @Autowired
    private SeckillRecordMapper recordMapper;

    @Autowired
    private SeckillActivitySkuMapper activitySkuMapper;

    @Autowired
    private MqOutboxMapper outboxMapper;

    @Autowired
    private SeckillStockRedis stockRedis;

    @Autowired
    private SeckillResultService resultService;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private HttpClient http;
    private String base;
    private String adminToken;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
        adminToken = tokenProvider.createAccessToken(1001L, Audience.ADMIN, ADMIN_TOKEN_ROLE);

        doAnswer(invocation -> {
            SkuSnapshot snapshot = new SkuSnapshot((Long) invocation.getArgument(0),
                    invocation.getArgument(0) + "-01", "默认规格", 9900L, "https://minio/sku.jpg",
                    true, SKU_SEQ.get(), "秒杀商品", "https://minio/p.jpg", "ON_SALE");
            return snapshot;
        }).when(productClient).sku(anyLong());
        doAnswer(invocation -> {
            AllocateStockRequest request = invocation.getArgument(0);
            return pool(request.activitySkuId(), request.quantity());
        }).when(inventoryClient).allocate(any());
        doAnswer(invocation -> pool(1L, 0)).when(inventoryClient).returnStock(any());
        doAnswer(invocation -> pool(invocation.getArgument(0), null)).when(inventoryClient).pool(anyLong());
    }

    private SeckillPoolView pool(long activitySkuId, Integer remaining) {
        return new SeckillPoolView(activitySkuId, 1L, activitySkuId, 1L, 100, remaining, 0, LocalDateTime.now());
    }

    private record Resp(int status, JsonNode json) {
    }

    private Resp send(String method, String path, String body, String token, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (requestId != null) {
            builder.header("X-Request-Id", requestId);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), objectMapper.readTree(response.body()));
    }

    private String userToken() {
        return tokenProvider.createAccessToken(USER_SEQ.incrementAndGet(), Audience.USER, null);
    }

    /** 建一个「正在进行」的活动，返回 activitySkuId */
    private long createRunningActivity(int allocStock, int limitPerUser) throws Exception {
        long skuId = SKU_SEQ.incrementAndGet();
        String start = LocalDateTime.now().minusMinutes(1).format(TIME);
        String end = LocalDateTime.now().plusHours(2).format(TIME);
        String body = "{\"name\":\"测试秒杀活动\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
                + "\",\"skus\":[{\"skuId\":" + skuId + ",\"seckillPrice\":100,\"allocStock\":" + allocStock
                + ",\"limitPerUser\":" + limitPerUser + "}]}";
        Resp created = send("POST", "/api/v1/admin/seckill/activities", body, adminToken, null);
        assertThat(created.json().get("code").asInt()).isZero();
        return created.json().get("data").get("skus").get(0).get("id").asLong();
    }

    private Resp buy(long activitySkuId, int quantity, String token, String requestId) throws Exception {
        return send("POST", "/api/v1/seckill/activities/skus/" + activitySkuId + "/orders",
                "{\"quantity\":" + quantity + ",\"requestId\":\"" + requestId + "\"}", token, requestId);
    }

    @Test
    @DisplayName("创建活动：校验时间与商品、写入活动 SKU、向 inventory 划拨并预热 Redis（REQ-901）")
    void createActivityAllocatesAndWarms() throws Exception {
        long activitySkuId = createRunningActivity(5, 2);
        assertThat(activitySkuId).isPositive();
        assertThat(activitySkuMapper.selectById(activitySkuId).getAllocStock()).isEqualTo(5);
        // Redis 已预热：库存键存在且等于划拨量
        assertThat(stockRedis.stockOf(activitySkuId)).isEqualTo(5);

        // 活动时间非法直接被拒
        String skuId = String.valueOf(SKU_SEQ.get());
        String ended = LocalDateTime.now().minusHours(1).format(TIME);
        assertThat(send("POST", "/api/v1/admin/seckill/activities",
                "{\"name\":\"x\",\"startTime\":\"" + ended + "\",\"endTime\":\"" + ended
                        + "\",\"skus\":[{\"skuId\":" + skuId + ",\"seckillPrice\":1,\"allocStock\":1}]}",
                adminToken, null).json().get("code").asInt()).isEqualTo(10001);
        assertThat(send("POST", "/api/v1/admin/seckill/activities",
                "{\"name\":\"x\",\"startTime\":\"" + LocalDateTime.now().plusHours(1).format(TIME)
                        + "\",\"endTime\":\"" + LocalDateTime.now().plusHours(2).format(TIME) + "\",\"skus\":[]}",
                adminToken, null).json().get("code").asInt()).isEqualTo(10001);
        // 秒杀价必须低于原价（替身返回的原价是 9900）
        assertThat(send("POST", "/api/v1/admin/seckill/activities",
                "{\"name\":\"x\",\"startTime\":\"" + LocalDateTime.now().minusMinutes(1).format(TIME)
                        + "\",\"endTime\":\"" + LocalDateTime.now().plusHours(1).format(TIME)
                        + "\",\"skus\":[{\"skuId\":" + skuId + ",\"seckillPrice\":9900,\"allocStock\":1}]}",
                adminToken, null).json().get("code").asInt()).isEqualTo(10001);
    }

    @Test
    @DisplayName("抢购：预扣成功返回排队票据，落 QUEUED 记录并写本地消息表；同 requestId 幂等（REQ-903）")
    void buyQueuesTicket() throws Exception {
        long activitySkuId = createRunningActivity(3, 2);
        String token = userToken();
        String requestId = "SEK-REQ-" + activitySkuId;

        JsonNode data = buy(activitySkuId, 2, token, requestId).json().get("data");
        String ticketId = data.get("ticketId").asString();
        assertThat(data.get("status").asString()).isEqualTo("QUEUED");
        assertThat(stockRedis.stockOf(activitySkuId)).isEqualTo(1);

        // 同一 requestId 重复请求：返回同一票据，不重复扣减
        JsonNode again = buy(activitySkuId, 2, token, requestId).json().get("data");
        assertThat(again.get("ticketId").asString()).isEqualTo(ticketId);
        assertThat(stockRedis.stockOf(activitySkuId)).isEqualTo(1);

        // 记录与消息都落库
        List<SeckillRecord> records = recordMapper.selectList(Wrappers.<SeckillRecord>lambdaQuery()
                .eq(SeckillRecord::getTicketId, ticketId));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo("QUEUED");
        assertThat(outboxMapper.selectCount(Wrappers.<MqOutbox>lambdaQuery()
                .eq(MqOutbox::getBizKey, ticketId))).isEqualTo(1);

        // 限购 2：再抢 1 件（换 requestId）应被拒（已购 2 + 1 > 2）
        assertThat(buy(activitySkuId, 1, token, requestId + "-2").json().get("code").asInt()).isEqualTo(70005);
    }

    @Test
    @DisplayName("抢购：未开始 / 已结束 / 售罄都有明确业务码（REQ-902、REQ-903）")
    void buyRejectsByActivityWindow() throws Exception {
        String token = userToken();
        long skuId = SKU_SEQ.incrementAndGet();
        String start = LocalDateTime.now().plusMinutes(10).format(TIME);
        String end = LocalDateTime.now().plusHours(1).format(TIME);
        long notStartedId = send("POST", "/api/v1/admin/seckill/activities",
                "{\"name\":\"未开始\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
                        + "\",\"skus\":[{\"skuId\":" + skuId + ",\"seckillPrice\":100,\"allocStock\":5}]}",
                adminToken, null).json().get("data").get("skus").get(0).get("id").asLong();
        assertThat(buy(notStartedId, 1, token, "SEK-NS-" + notStartedId).json().get("code").asInt()).isEqualTo(70002);

        // 已结束的活动：用同一个活动把结束时间提前（管理端改时间 + 重新预热）
        send("PUT", "/api/v1/admin/seckill/activities/"
                        + activityIdOf(notStartedId),
                "{\"name\":\"已结束\",\"startTime\":\"" + LocalDateTime.now().minusHours(2).format(TIME)
                        + "\",\"endTime\":\"" + LocalDateTime.now().minusHours(1).format(TIME) + "\"}",
                adminToken, null);
        assertThat(buy(notStartedId, 1, token, "SEK-ED-" + notStartedId).json().get("code").asInt()).isEqualTo(70003);

        // 售罄：库存 1，抢 1 成功后再抢即为售罄
        long soldOutId = createRunningActivity(1, 1);
        assertThat(buy(soldOutId, 1, userToken(), "SEK-S1-" + soldOutId).json().get("code").asInt()).isZero();
        assertThat(buy(soldOutId, 1, userToken(), "SEK-S2-" + soldOutId).json().get("code").asInt()).isEqualTo(70004);
    }

    @Test
    @DisplayName("并发抢购：N 份库存只成功 N 次，绝不超卖（ADR-008 的并发验收）")
    void concurrentBuyNeverOversells() throws Exception {
        int stock = 5;
        long activitySkuId = createRunningActivity(stock, 1);
        int threads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String token = userToken();
            String requestId = "SEK-CONC-" + activitySkuId + "-" + i;
            tasks.add(() -> buy(activitySkuId, 1, token, requestId).json().get("code").asInt());
        }
        List<Future<Integer>> results = pool.invokeAll(tasks);
        pool.shutdown();

        int success = 0;
        int soldOut = 0;
        for (Future<Integer> future : results) {
            int code = future.get();
            if (code == 0) {
                success++;
            } else if (code == 70004) {
                soldOut++;
            }
        }
        assertThat(success).as("成功次数必须恰好等于库存数").isEqualTo(stock);
        assertThat(soldOut).isEqualTo(threads - stock);
        assertThat(stockRedis.stockOf(activitySkuId)).isZero();
    }

    @Test
    @DisplayName("结果轮询与失败回补：下单失败时库存与限购计数都回到可再次抢购（REQ-904）")
    void resultPollingAndFailureRollback() throws Exception {
        long activitySkuId = createRunningActivity(2, 1);
        String token = userToken();
        String ticketId = buy(activitySkuId, 1, token, "SEK-RES-" + activitySkuId)
                .json().get("data").get("ticketId").asString();

        // 排队中
        JsonNode queued = send("GET", "/api/v1/seckill/results/" + ticketId, null, token, null)
                .json().get("data");
        assertThat(queued.get("status").asString()).isEqualTo("QUEUED");

        // order 侧回写失败：秒杀侧回补 Redis
        resultService.applyResult(ticketId, new SeckillResultRequest("FAILED", null, "SOLD_OUT", "秒杀商品已售罄"));
        JsonNode failed = send("GET", "/api/v1/seckill/results/" + ticketId, null, token, null)
                .json().get("data");
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("failReason").asString()).isEqualTo("SOLD_OUT");
        assertThat(failed.get("message").asString()).isEqualTo("秒杀商品已售罄");
        assertThat(stockRedis.stockOf(activitySkuId)).isEqualTo(2);

        // 失败后同一用户还能再抢（限购计数已回退）
        assertThat(buy(activitySkuId, 1, token, "SEK-RES2-" + activitySkuId).json().get("code").asInt()).isZero();
    }

    @Test
    @DisplayName("订单取消：回补秒杀池并恢复限购计数，可再次抢购（REQ-905）")
    void orderCancelReturnsStockToPool() throws Exception {
        long activitySkuId = createRunningActivity(2, 1);
        String token = userToken();
        String ticketId = buy(activitySkuId, 1, token, "SEK-CAN-" + activitySkuId)
                .json().get("data").get("ticketId").asString();
        String orderNo = "20261005000000000001";
        resultService.applyResult(ticketId, new SeckillResultRequest("SUCCESS", orderNo, null, null));
        assertThat(stockRedis.stockOf(activitySkuId)).isEqualTo(1);

        resultService.onOrderCancelled(orderNo);

        SeckillRecord record = recordMapper.selectOne(Wrappers.<SeckillRecord>lambdaQuery()
                .eq(SeckillRecord::getTicketId, ticketId));
        assertThat(record.getStatus()).isEqualTo("FAILED");
        assertThat(record.getFailReason()).isEqualTo("ORDER_CANCELLED");
        assertThat(stockRedis.stockOf(activitySkuId)).isEqualTo(2);
        // 重复取消不再回补（幂等）
        resultService.onOrderCancelled(orderNo);
        assertThat(stockRedis.stockOf(activitySkuId)).isEqualTo(2);
        // 用户可再抢
        assertThat(buy(activitySkuId, 1, token, "SEK-CAN2-" + activitySkuId).json().get("code").asInt()).isZero();
    }

    @Test
    @DisplayName("防刷：同一用户高频请求被限流（REQ-906）")
    void rateLimitRejectsBurst() throws Exception {
        long activitySkuId = createRunningActivity(50, 50);
        String token = userToken();
        boolean limited = false;
        for (int i = 0; i < 30; i++) {
            int code = buy(activitySkuId, 1, token, "SEK-RL-" + activitySkuId + "-" + i).json().get("code").asInt();
            if (code == 10005) {
                limited = true;
                break;
            }
        }
        assertThat(limited).as("同一用户连续请求应触发限流").isTrue();
    }

    @Test
    @DisplayName("其它：活动不存在 / 未登录 / 查不到票据")
    void miscValidations() throws Exception {
        assertThat(send("GET", "/api/v1/seckill/activities/99999999", null, null, null)
                .json().get("code").asInt()).isEqualTo(70001);

        long activitySkuId = createRunningActivity(3, 1);
        assertThat(buy(activitySkuId, 1, null, "SEK-ANON-" + activitySkuId).json().get("code").asInt())
                .isEqualTo(10002);

        String token = userToken();
        assertThat(send("GET", "/api/v1/seckill/results/TK-NOT-EXIST", null, token, null)
                .json().get("code").asInt()).isEqualTo(70007);
        // 他人票据不可见
        String ticketId = buy(activitySkuId, 1, token, "SEK-OWN-" + activitySkuId)
                .json().get("data").get("ticketId").asString();
        assertThat(send("GET", "/api/v1/seckill/results/" + ticketId, null, userToken(), null)
                .json().get("code").asInt()).isEqualTo(70007);
    }

    private long activityIdOf(long activitySkuId) {
        return activitySkuMapper.selectById(activitySkuId).getActivityId();
    }
}
