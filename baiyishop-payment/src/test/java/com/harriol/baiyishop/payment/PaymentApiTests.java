package com.harriol.baiyishop.payment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.payment.client.OrderClient;
import com.harriol.baiyishop.payment.config.PaymentProperties;
import com.harriol.baiyishop.payment.dto.PayableOrderView;
import com.harriol.baiyishop.payment.entity.MqOutbox;
import com.harriol.baiyishop.payment.entity.Payment;
import com.harriol.baiyishop.payment.entity.PaymentCallbackLog;
import com.harriol.baiyishop.payment.mapper.MqOutboxMapper;
import com.harriol.baiyishop.payment.mapper.PaymentCallbackLogMapper;
import com.harriol.baiyishop.payment.mapper.PaymentMapper;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 支付端到端验证（REQ-801 ~ REQ-803）。
 * <p>order-service 用 Mockito 替身顶掉（本用例只验证支付侧规则：金额与状态校验、同订单复用支付单、
 * 验签、回调幂等、事件入箱）；跨服务真实链路由端到端冒烟覆盖。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentApiTests {

    private static final long ID_BASE = 3_000_000_000L + System.currentTimeMillis() % 100_000_000L;
    private static final AtomicLong USER_SEQ = new AtomicLong(ID_BASE);
    private static final AtomicLong ORDER_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    @TestConfiguration
    static class StubClients {
        @Bean
        @Primary
        OrderClient orderClient() {
            return Mockito.mock(OrderClient.class);
        }
    }

    @Autowired
    private OrderClient orderClient;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private PaymentCallbackLogMapper callbackLogMapper;

    @Autowired
    private MqOutboxMapper outboxMapper;

    @Autowired
    private PaymentProperties properties;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private HttpClient http;
    private String base;
    private long userId;
    private String userToken;
    private String orderNo;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
        userId = USER_SEQ.incrementAndGet();
        userToken = tokenProvider.createAccessToken(userId, Audience.USER, null);
        orderNo = String.valueOf(ORDER_SEQ.incrementAndGet());

        doAnswer(invocation -> payableOrder((String) invocation.getArgument(0), userId, "PENDING_PAYMENT"))
                .when(orderClient).payable(anyString());
    }

    private PayableOrderView payableOrder(String requestedOrderNo, long ownerId, String status) {
        return new PayableOrderView(requestedOrderNo, ownerId, status, 19800L,
                LocalDateTime.now().plusMinutes(15));
    }

    private record Resp(int status, JsonNode json, String rawBody) {
    }

    private Resp send(String method, String path, String body, String token, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
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
        JsonNode json = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        return new Resp(response.statusCode(), json, response.body());
    }

    private Resp createPayment(String channel) throws Exception {
        return send("POST", "/api/v1/payments",
                "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"" + channel + "\"}", userToken, "PAY-REQ-" + orderNo);
    }

    private String sign(String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.mockSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    }

    private Resp callback(String paymentNo, String tradeNo, long amount, String status, boolean signed) throws Exception {
        String body = "{\"paymentNo\":\"" + paymentNo + "\",\"channelTradeNo\":\"" + tradeNo
                + "\",\"amount\":" + amount + ",\"status\":\"" + status + "\"}";
        String signature = signed ? sign(body) : "deadbeef";
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/payments/callback/WECHAT"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Pay-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), objectMapper.readTree(response.body()), response.body());
    }

    private Payment paymentOf(String paymentNo) {
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getPaymentNo, paymentNo));
    }

    @Test
    @DisplayName("发起支付：返回渠道支付参数，金额取自订单（不信任前端）")
    void createReturnsChannelPayParams() throws Exception {
        JsonNode wechat = createPayment("WECHAT").json().get("data");
        assertThat(wechat.get("amount").asLong()).isEqualTo(19800);
        assertThat(wechat.get("paymentNo").asString()).startsWith("PAY");
        assertThat(wechat.get("payParams").get("prepayId").asString()).startsWith("mock_prepay_");
        assertThat(wechat.get("payParams").get("signType").asString()).isEqualTo("RSA");
        assertThat(wechat.get("expireAt").asString()).isNotBlank();

        // 换支付宝渠道：同一订单复用支付单，但支付参数按渠道生成
        JsonNode alipay = createPayment("ALIPAY").json().get("data");
        assertThat(alipay.get("paymentNo").asString()).isEqualTo(wechat.get("paymentNo").asString());
        assertThat(alipay.get("payParams").get("signType").asString()).isEqualTo("RSA2");

        assertThat(paymentMapper.selectCount(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderNo, orderNo))).isEqualTo(1);
    }

    @Test
    @DisplayName("发起支付：不可支付的订单 / 他人订单 / 缺请求标识 / 未知渠道都被拒")
    void createValidatesOrderAndChannel() throws Exception {
        doAnswer(invocation -> payableOrder((String) invocation.getArgument(0), userId, "PENDING_SHIPMENT"))
                .when(orderClient).payable(anyString());
        assertThat(createPayment("WECHAT").json().get("code").asInt()).isEqualTo(60002);

        doAnswer(invocation -> payableOrder((String) invocation.getArgument(0), userId + 999, "PENDING_PAYMENT"))
                .when(orderClient).payable(anyString());
        assertThat(createPayment("WECHAT").json().get("code").asInt()).isEqualTo(50001);

        doAnswer(invocation -> payableOrder((String) invocation.getArgument(0), userId, "PENDING_PAYMENT"))
                .when(orderClient).payable(anyString());
        assertThat(send("POST", "/api/v1/payments",
                "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"WECHAT\"}", userToken, null)
                .json().get("code").asInt()).isEqualTo(10001);
        assertThat(send("POST", "/api/v1/payments",
                "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"UNIONPAY\"}", userToken, "PAY-REQ-X")
                .json().get("code").asInt()).isEqualTo(10001);
        assertThat(send("POST", "/api/v1/payments",
                "{\"orderNo\":\"" + orderNo + "\",\"channel\":\"WECHAT\"}", null, "PAY-REQ-Y")
                .json().get("code").asInt()).isEqualTo(10002);
    }

    @Test
    @DisplayName("回调验签：签名不对拒绝处理并落一条 sign_verified=0 的记录（REQ-802-1）")
    void callbackRejectsBadSignature() throws Exception {
        String paymentNo = createPayment("WECHAT").json().get("data").get("paymentNo").asString();

        Resp response = callback(paymentNo, "WX-BAD-" + orderNo, 19800, "SUCCESS", false);
        assertThat(response.json().get("code").asInt()).isEqualTo(60004);
        assertThat(paymentOf(paymentNo).getStatus()).isEqualTo("PENDING");

        // 验签失败时拿不到可信流水号与支付单号，日志用报文哈希占位（paymentNo 为空）
        List<PaymentCallbackLog> logs = callbackLogMapper.selectList(Wrappers.<PaymentCallbackLog>lambdaQuery()
                .eq(PaymentCallbackLog::getSignVerified, 0)
                .likeRight(PaymentCallbackLog::getChannelTradeNo, "UNVERIFIED-"));
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getProcessResult()).contains("验签失败");
    }

    @Test
    @DisplayName("回调成功：支付单置成功并写支付成功事件；重复回调只生效一次（REQ-802-2、REQ-802-3）")
    void callbackIsIdempotent() throws Exception {
        String paymentNo = createPayment("WECHAT").json().get("data").get("paymentNo").asString();

        // 渠道流水号在全库唯一（uk_channel_trade），用本次运行的唯一订单号拼出来，
        // 否则上一轮运行留下的日志会把这次回调判成「重复」
        String tradeNo = "WX-TRADE-" + orderNo;
        Resp first = callback(paymentNo, tradeNo, 19800, "SUCCESS", true);
        assertThat(first.json().get("code").asString()).isEqualTo("SUCCESS");

        Payment payment = paymentOf(paymentNo);
        assertThat(payment.getStatus()).isEqualTo("SUCCESS");
        assertThat(payment.getChannelTradeNo()).isEqualTo(tradeNo);
        assertThat(payment.getPayTime()).isNotNull();
        assertThat(outboxCount(orderNo)).isEqualTo(1);

        Resp second = callback(paymentNo, tradeNo, 19800, "SUCCESS", true);
        assertThat(second.json().get("message").asString()).isEqualTo("DUPLICATE_IGNORED");
        assertThat(outboxCount(orderNo)).isEqualTo(1);
    }

    @Test
    @DisplayName("回调校验：金额与支付单不一致返回 60003，支付单不存在返回 60001")
    void callbackValidatesAmountAndPayment() throws Exception {
        String paymentNo = createPayment("WECHAT").json().get("data").get("paymentNo").asString();

        assertThat(callback(paymentNo, "WX-AMOUNT-" + orderNo, 1, "SUCCESS", true).json().get("code").asInt())
                .isEqualTo(60003);
        assertThat(paymentOf(paymentNo).getStatus()).isEqualTo("PENDING");

        assertThat(callback("PAY-NOT-EXIST-" + orderNo, "WX-NO-" + orderNo, 100, "SUCCESS", true)
                .json().get("code").asInt())
                .isEqualTo(60001);
    }

    @Test
    @DisplayName("模拟渠道一键支付：走真实回调链路（验签 + 幂等 + 事件）")
    void mockPayCompletesPayment() throws Exception {
        String paymentNo = createPayment("WECHAT").json().get("data").get("paymentNo").asString();

        JsonNode data = send("POST", "/api/v1/payments/" + paymentNo + "/mock-pay", "{}", userToken, null)
                .json().get("data");
        assertThat(data.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(data.get("channelTradeNo").asString()).startsWith("MOCK");
        assertThat(outboxCount(orderNo)).isEqualTo(1);
        // 再点一次：幂等返回，不产生第二条事件
        send("POST", "/api/v1/payments/" + paymentNo + "/mock-pay", "{}", userToken, null);
        assertThat(outboxCount(orderNo)).isEqualTo(1);
    }

    @Test
    @DisplayName("查询：按支付单与按订单都能查，且只能查自己的（REQ-803）")
    void queryEndpoints() throws Exception {
        String paymentNo = createPayment("WECHAT").json().get("data").get("paymentNo").asString();
        callback(paymentNo, "WX-TRADE-Q-" + orderNo, 19800, "SUCCESS", true);

        JsonNode byNo = send("GET", "/api/v1/payments/" + paymentNo, null, userToken, null).json().get("data");
        assertThat(byNo.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(byNo.get("orderNo").asString()).isEqualTo(orderNo);

        JsonNode byOrder = send("GET", "/api/v1/payments/by-order/" + orderNo, null, userToken, null)
                .json().get("data");
        assertThat(byOrder.get("paymentNo").asString()).isEqualTo(paymentNo);

        String otherUser = tokenProvider.createAccessToken(USER_SEQ.incrementAndGet(), Audience.USER, null);
        assertThat(send("GET", "/api/v1/payments/" + paymentNo, null, otherUser, null).json().get("code").asInt())
                .isEqualTo(60001);
        assertThat(send("GET", "/api/v1/payments/PAY-NOT-EXIST", null, userToken, null)
                .json().get("code").asInt()).isEqualTo(60001);
    }

    private long outboxCount(String bizKey) {
        return outboxMapper.selectCount(Wrappers.<MqOutbox>lambdaQuery().eq(MqOutbox::getBizKey, bizKey));
    }
}
