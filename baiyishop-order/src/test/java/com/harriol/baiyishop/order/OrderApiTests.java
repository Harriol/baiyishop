package com.harriol.baiyishop.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.order.client.InventoryClient;
import com.harriol.baiyishop.order.client.ProductClient;
import com.harriol.baiyishop.order.client.UserClient;
import com.harriol.baiyishop.order.domain.OrderStatus;
import com.harriol.baiyishop.order.dto.AddressSnapshot;
import com.harriol.baiyishop.order.dto.SkuSnapshot;
import com.harriol.baiyishop.order.entity.MqOutbox;
import com.harriol.baiyishop.order.entity.Order;
import com.harriol.baiyishop.order.entity.OrderItem;
import com.harriol.baiyishop.order.entity.OrderStatusLog;
import com.harriol.baiyishop.order.mapper.MqOutboxMapper;
import com.harriol.baiyishop.order.mapper.OrderItemMapper;
import com.harriol.baiyishop.order.mapper.OrderMapper;
import com.harriol.baiyishop.order.mapper.OrderStatusLogMapper;
import com.harriol.baiyishop.order.service.OrderService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 订单主流程端到端验证（REQ-701 ~ REQ-708）。
 * <p>商品 / 库存 / 地址三个跨服务依赖用 Mockito 替身顶掉：
 * 本用例验证订单自身的规则（金额服务端计算、幂等、状态机、留痕、消息入箱），
 * 跨服务真实链路（含 Seata 全局回滚）由冒烟测试覆盖。
 * <p>{@code seata.enabled=false}（见 test/resources/application.properties）：
 * 单个服务无法验证全局事务，这里只跑本地逻辑。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiTests {

    /** 每次运行换一批 id，避免上一轮运行留下的订单 / 购物车数据串味 */
    private static final long ID_BASE = 2_000_000_000L + System.currentTimeMillis() % 100_000_000L;
    private static final AtomicLong USER_SEQ = new AtomicLong(ID_BASE);
    private static final AtomicLong SKU_SEQ = new AtomicLong(ID_BASE);
    private static final AtomicLong REQUEST_SEQ = new AtomicLong(System.currentTimeMillis());

    @TestConfiguration
    static class StubClients {
        @Bean
        @Primary
        ProductClient productClient() {
            return Mockito.mock(ProductClient.class);
        }

        @Bean
        @Primary
        InventoryClient inventoryClient() {
            return Mockito.mock(InventoryClient.class);
        }

        @Bean
        @Primary
        UserClient userClient() {
            return Mockito.mock(UserClient.class);
        }
    }

    @Autowired
    private ProductClient productClient;

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private UserClient userClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderStatusLogMapper statusLogMapper;

    @Autowired
    private MqOutboxMapper outboxMapper;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private HttpClient http;
    private String base;
    private String userToken;
    private long userId;
    private String adminToken;

    private final Map<Long, SkuSnapshot> skus = new HashMap<>();
    private final Map<Long, AddressSnapshot> addresses = new HashMap<>();
    private final Map<Long, Integer> stock = new HashMap<>();
    private final AtomicInteger lockCalls = new AtomicInteger();
    private final AtomicInteger releaseCalls = new AtomicInteger();
    private final AtomicInteger deductCalls = new AtomicInteger();
    /** 结算页默认地址（地址表按 userId 查，与 addresses 的 key（addressId）不同） */
    private Long defaultAddressId;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
        userId = USER_SEQ.incrementAndGet();
        userToken = tokenProvider.createAccessToken(userId, Audience.USER, null);
        adminToken = tokenProvider.createAccessToken(ID_BASE, Audience.ADMIN, "SUPER_ADMIN");
        skus.clear();
        addresses.clear();
        stock.clear();
        lockCalls.set(0);
        releaseCalls.set(0);
        deductCalls.set(0);
        defaultAddressId = null;

        doAnswer(invocation -> {
            Long skuId = invocation.getArgument(0);
            SkuSnapshot snapshot = skus.get(skuId);
            if (snapshot == null) {
                throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            return snapshot;
        }).when(productClient).sku(anyLong());
        doAnswer(invocation -> new HashMap<>(skus)).when(productClient).skus(any());
        doAnswer(invocation -> new HashMap<>(stock)).when(inventoryClient).availableBatch(any());
        doAnswer(invocation -> {
            lockCalls.incrementAndGet();
            return null;
        }).when(inventoryClient).lock(anyString(), any());
        doAnswer(invocation -> {
            releaseCalls.incrementAndGet();
            return null;
        }).when(inventoryClient).release(anyString(), any());
        doAnswer(invocation -> {
            deductCalls.incrementAndGet();
            return null;
        }).when(inventoryClient).deduct(anyString(), any());
        doAnswer(invocation -> Optional.ofNullable(addresses.get(invocation.getArgument(0))))
                .when(userClient).address(anyLong());
        doAnswer(invocation -> Optional.ofNullable(defaultAddressId).map(addresses::get))
                .when(userClient).defaultAddress(anyLong());
        doAnswer(invocation -> "测试管理员").when(userClient).adminName(anyLong());
    }

    private record Resp(int status, JsonNode json) {
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
        return new Resp(response.statusCode(), objectMapper.readTree(response.body()));
    }

    private long newSku(long price, int available) {
        long skuId = SKU_SEQ.incrementAndGet();
        long productId = skuId;
        skus.put(skuId, new SkuSnapshot(skuId, productId + "-01", "默认规格", price,
                "https://minio/sku-" + skuId + ".jpg", true, productId, "商品" + productId,
                "https://minio/p.jpg", "ON_SALE"));
        stock.put(skuId, available);
        return skuId;
    }

    private long newAddress() {
        long addressId = SKU_SEQ.incrementAndGet();
        addresses.put(addressId, new AddressSnapshot(addressId, userId, "张三", "13800000000",
                "浙江省", "杭州市", "西湖区", "文一西路 1 号"));
        defaultAddressId = addressId;
        return addressId;
    }

    private static String requestId() {
        return "REQ" + REQUEST_SEQ.incrementAndGet();
    }

    /** 购物车接口按 id 倒序返回，取条目 id 要按 skuId 找，不能按下标 */
    private long itemIdOf(JsonNode cart, long skuId) {
        for (JsonNode item : cart.get("items")) {
            if (item.get("skuId").asLong() == skuId) {
                return item.get("id").asLong();
            }
        }
        throw new AssertionError("购物车里找不到 skuId=" + skuId);
    }

    private Resp buyNow(long skuId, int quantity, long addressId, String requestId) throws Exception {
        return send("POST", "/api/v1/orders",
                "{\"source\":\"BUY_NOW\",\"skuId\":" + skuId + ",\"quantity\":" + quantity
                        + ",\"addressId\":" + addressId + ",\"remark\":\"尽快发货\"}", userToken, requestId);
    }

    private Order orderOf(String orderNo) {
        return orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
    }

    private void arrangeStatus(String orderNo, OrderStatus status, LocalDateTime autoReceiveAt) {
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .set(Order::getStatus, status.name())
                .set(autoReceiveAt != null, Order::getAutoReceiveAt, autoReceiveAt));
    }

    @Test
    @DisplayName("立即购买下单：服务端算金额、锁库存用同一 orderNo、写状态日志与超时消息")
    void createOrderLocksStockAndWritesOutbox() throws Exception {
        long skuId = newSku(9900, 10);
        long addressId = newAddress();

        Resp resp = buyNow(skuId, 2, addressId, requestId());
        assertThat(resp.json().get("code").asInt()).isZero();
        JsonNode data = resp.json().get("data");
        String orderNo = data.get("orderNo").asString();
        assertThat(data.get("payAmount").asLong()).isEqualTo(19800);
        assertThat(data.get("status").asString()).isEqualTo("PENDING_PAYMENT");

        Order order = orderOf(orderNo);
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getTotalAmount()).isEqualTo(19800);
        assertThat(order.getFreightAmount()).isZero();
        assertThat(order.getReceiverName()).isEqualTo("张三");
        assertThat(order.getReceiverPhone()).isEqualTo("13800000000");
        assertThat(order.getReceiverAddress()).isEqualTo("浙江省杭州市西湖区文一西路 1 号");
        assertThat(order.getTimeoutAt()).isBetween(order.getCreatedAt().plusMinutes(15).minusSeconds(5),
                order.getCreatedAt().plusMinutes(15).plusSeconds(5));

        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderNo, orderNo));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getUnitPrice()).isEqualTo(9900);
        assertThat(items.get(0).getTotalAmount()).isEqualTo(19800);
        assertThat(items.get(0).getProductName()).startsWith("商品");

        List<OrderStatusLog> logs = statusLogMapper.selectList(
                Wrappers.<OrderStatusLog>lambdaQuery().eq(OrderStatusLog::getOrderNo, orderNo));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getFromStatus()).isNull();
        assertThat(logs.get(0).getToStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(logs.get(0).getOperatorType()).isEqualTo("SYSTEM");

        List<MqOutbox> outbox = outboxMapper.selectList(Wrappers.<MqOutbox>lambdaQuery()
                .eq(MqOutbox::getBizKey, orderNo));
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getTopic()).isEqualTo("baiyishop-order-timeout");
        assertThat(outbox.get(0).getStatus()).isEqualTo(MqOutbox.STATUS_PENDING);
        // 延时投递时间 = 支付超时时间（15 分钟后）
        assertThat(outbox.get(0).getDeliverAt()).isEqualTo(order.getTimeoutAt());
        assertThat(objectMapper.readTree(outbox.get(0).getPayload()).get("orderNo").asString()).isEqualTo(orderNo);
        assertThat(lockCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("同一 X-Request-Id 重复提交只产生一笔订单（REQ-701 约定 2）")
    void createOrderIsIdempotent() throws Exception {
        long skuId = newSku(500, 10);
        long addressId = newAddress();
        String requestId = requestId();

        String first = buyNow(skuId, 1, addressId, requestId).json().get("data").get("orderNo").asString();
        String second = buyNow(skuId, 1, addressId, requestId).json().get("data").get("orderNo").asString();

        assertThat(second).isEqualTo(first);
        assertThat(lockCalls.get()).isEqualTo(1);
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId))).isEqualTo(1);
    }

    @Test
    @DisplayName("库存不足：返回 40001 且不产生订单（REQ-701 约定 3）")
    void insufficientStockProducesNoOrder() throws Exception {
        long skuId = newSku(500, 1);
        long addressId = newAddress();
        doAnswer(invocation -> {
            lockCalls.incrementAndGet();
            throw new BizException(ErrorCode.INSUFFICIENT_STOCK, "库存不足，无法完成操作");
        }).when(inventoryClient).lock(anyString(), any());

        Resp resp = buyNow(skuId, 5, addressId, requestId());
        assertThat(resp.json().get("code").asInt()).isEqualTo(40001);
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId))).isZero();
        // 库存侧被调用过一次（失败即抛出），但订单没有落库
        assertThat(lockCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("下单校验：未选地址 50005、他人地址 20006、超单品限购 50006、下架 30008")
    void createOrderValidatesInput() throws Exception {
        long skuId = newSku(500, 10);
        long addressId = newAddress();

        assertThat(send("POST", "/api/v1/orders",
                "{\"source\":\"BUY_NOW\",\"skuId\":" + skuId + ",\"quantity\":1}", userToken, requestId())
                .json().get("code").asInt()).isEqualTo(50005);

        long otherAddress = SKU_SEQ.incrementAndGet();
        addresses.put(otherAddress, new AddressSnapshot(otherAddress, userId + 1, "李四", "13900000000",
                "江苏省", "南京市", "玄武区", "中山路 2 号"));
        assertThat(buyNow(skuId, 1, otherAddress, requestId()).json().get("code").asInt()).isEqualTo(20006);

        assertThat(buyNow(skuId, 100, addressId, requestId()).json().get("code").asInt()).isEqualTo(50006);
        assertThat(send("POST", "/api/v1/orders",
                "{\"source\":\"BUY_NOW\",\"skuId\":" + skuId + "}", userToken, requestId())
                .json().get("code").asInt()).isEqualTo(10001);

        // 商品下架后不能下单
        long offSale = newSku(600, 10);
        skus.put(offSale, new SkuSnapshot(offSale, offSale + "-01", "默认规格", 600L, null, true,
                offSale, "商品" + offSale, null, "OFF_SALE"));
        assertThat(buyNow(offSale, 1, addressId, requestId()).json().get("code").asInt()).isEqualTo(30008);

        assertThat(send("POST", "/api/v1/orders",
                "{\"source\":\"BUY_NOW\",\"skuId\":" + skuId + ",\"quantity\":1,\"addressId\":" + addressId + "}",
                userToken, null).json().get("code").asInt()).isEqualTo(10001);
    }

    @Test
    @DisplayName("购物车结算：只结算勾选项，下单后清理已结算条目（REQ-602）")
    void createFromCartClearsSettledItems() throws Exception {
        long first = newSku(1000, 10);
        long second = newSku(2000, 10);
        long addressId = newAddress();

        long firstItemId = send("POST", "/api/v1/carts/items",
                "{\"skuId\":" + first + ",\"quantity\":2}", userToken, null)
                .json().get("data").get("items").get(0).get("id").asLong();
        Resp cart = send("POST", "/api/v1/carts/items",
                "{\"skuId\":" + second + ",\"quantity\":1}", userToken, null);
        long secondItemId = itemIdOf(cart.json().get("data"), second);

        // 结算试算：应只包含指定的条目
        JsonNode settle = send("POST", "/api/v1/orders/settle",
                "{\"source\":\"CART\",\"cartItemIds\":[" + firstItemId + "]}", userToken, null)
                .json().get("data");
        assertThat(settle.get("totalAmount").asLong()).isEqualTo(2000);
        assertThat(settle.get("payAmount").asLong()).isEqualTo(2000);
        assertThat(settle.get("freightAmount").asLong()).isZero();
        assertThat(settle.get("defaultAddress").get("receiverName").asString()).isEqualTo("张三");

        Resp created = send("POST", "/api/v1/orders",
                "{\"source\":\"CART\",\"cartItemIds\":[" + firstItemId + "],\"addressId\":" + addressId + "}",
                userToken, requestId());
        assertThat(created.json().get("code").asInt()).isZero();
        assertThat(created.json().get("data").get("payAmount").asLong()).isEqualTo(2000);

        JsonNode after = send("GET", "/api/v1/carts", null, userToken, null).json().get("data");
        assertThat(after.get("items").size()).isEqualTo(1);
        assertThat(after.get("items").get(0).get("id").asLong()).isEqualTo(secondItemId);
    }

    @Test
    @DisplayName("取消订单：仅待付款可取消，取消后释放库存并留痕（REQ-703、REQ-705）")
    void cancelReleasesStock() throws Exception {
        long skuId = newSku(700, 10);
        long addressId = newAddress();
        String orderNo = buyNow(skuId, 1, addressId, requestId()).json().get("data").get("orderNo").asString();

        assertThat(send("PUT", "/api/v1/orders/" + orderNo + "/cancel", null, userToken, null)
                .json().get("code").asInt()).isZero();

        Order order = orderOf(orderNo);
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getCancelReason()).isEqualTo("用户取消");
        assertThat(order.getCancelTime()).isNotNull();
        assertThat(releaseCalls.get()).isEqualTo(1);

        List<OrderStatusLog> logs = statusLogMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                .eq(OrderStatusLog::getOrderNo, orderNo)
                .orderByAsc(OrderStatusLog::getId));
        assertThat(logs).hasSize(2);
        assertThat(logs.get(1).getFromStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(logs.get(1).getToStatus()).isEqualTo("CANCELLED");
        assertThat(logs.get(1).getOperatorType()).isEqualTo("USER");
        assertThat(logs.get(1).getOperatorId()).isEqualTo(userId);

        // 重复取消：状态已变，返回 50002 且不再释放库存
        assertThat(send("PUT", "/api/v1/orders/" + orderNo + "/cancel", null, userToken, null)
                .json().get("code").asInt()).isEqualTo(50002);
        assertThat(releaseCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("超时取消：未到时间不动，到点后释放库存并幂等（REQ-704）")
    void timeoutCancelIsIdempotent() throws Exception {
        long skuId = newSku(800, 10);
        long addressId = newAddress();
        String orderNo = buyNow(skuId, 1, addressId, requestId()).json().get("data").get("orderNo").asString();

        // 还没到超时时间：消息提前到达也必须是空操作
        orderService.cancelByTimeout(orderNo);
        assertThat(orderOf(orderNo).getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(releaseCalls.get()).isZero();

        // 把超时时间拨到过去，模拟 15 分钟后
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .set(Order::getTimeoutAt, LocalDateTime.now().minusSeconds(1)));
        orderService.cancelByTimeout(orderNo);
        orderService.cancelByTimeout(orderNo);

        Order order = orderOf(orderNo);
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getCancelReason()).isEqualTo("超时未支付");
        assertThat(releaseCalls.get()).isEqualTo(1);
        assertThat(statusLogMapper.selectCount(Wrappers.<OrderStatusLog>lambdaQuery()
                .eq(OrderStatusLog::getOrderNo, orderNo))).isEqualTo(2);
    }

    @Test
    @DisplayName("确认收货与自动确认收货：仅待收货可流转（REQ-706、REQ-707）")
    void receiveAndAutoReceive() throws Exception {
        long skuId = newSku(900, 10);
        long addressId = newAddress();
        String payable = buyNow(skuId, 1, addressId, requestId()).json().get("data").get("orderNo").asString();

        // 未发货不能确认收货
        assertThat(send("PUT", "/api/v1/orders/" + payable + "/receive", null, userToken, null)
                .json().get("code").asInt()).isEqualTo(50002);

        arrangeStatus(payable, OrderStatus.PENDING_RECEIPT, LocalDateTime.now().plusDays(7));
        assertThat(send("PUT", "/api/v1/orders/" + payable + "/receive", null, userToken, null)
                .json().get("code").asInt()).isZero();
        assertThat(orderOf(payable).getStatus()).isEqualTo("COMPLETED");
        assertThat(orderOf(payable).getReceiveTime()).isNotNull();

        // 自动确认收货：未到时间不动，到点后完成
        String auto = buyNow(skuId, 1, addressId, requestId()).json().get("data").get("orderNo").asString();
        arrangeStatus(auto, OrderStatus.PENDING_RECEIPT, LocalDateTime.now().plusDays(7));
        orderService.autoReceive(auto);
        assertThat(orderOf(auto).getStatus()).isEqualTo("PENDING_RECEIPT");

        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, auto)
                .set(Order::getAutoReceiveAt, LocalDateTime.now().minusSeconds(1)));
        orderService.autoReceive(auto);
        assertThat(orderOf(auto).getStatus()).isEqualTo("COMPLETED");
        assertThat(statusLogMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                        .eq(OrderStatusLog::getOrderNo, auto).orderByAsc(OrderStatusLog::getId)).get(1)
                .getOperatorType()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("后台发货：仅待发货可发货，成功记录运单号并安排 7 天自动收货（REQ-707、REQ-708）")
    void adminShipSchedulesAutoReceive() throws Exception {
        long skuId = newSku(1100, 10);
        long addressId = newAddress();
        String orderNo = buyNow(skuId, 1, addressId, requestId()).json().get("data").get("orderNo").asString();

        // 待付款不能发货
        assertThat(send("POST", "/api/v1/admin/orders/" + orderNo + "/ship",
                "{\"trackingNo\":\"SF123\"}", adminToken, null).json().get("code").asInt()).isEqualTo(50002);

        // 走真实支付链路：支付成功事件把订单推进到待发货（REQ-802-3）
        orderService.markPaid(orderNo, "WECHAT", "WX-" + orderNo);
        assertThat(send("POST", "/api/v1/admin/orders/" + orderNo + "/ship",
                "{\"trackingNo\":\"SF1234567890\"}", adminToken, null).json().get("code").asInt()).isZero();

        Order order = orderOf(orderNo);
        assertThat(order.getStatus()).isEqualTo("PENDING_RECEIPT");
        assertThat(order.getTrackingNo()).isEqualTo("SF1234567890");
        assertThat(order.getShipTime()).isNotNull();
        assertThat(order.getAutoReceiveAt()).isEqualTo(order.getShipTime().plusDays(7));

        List<MqOutbox> outbox = outboxMapper.selectList(Wrappers.<MqOutbox>lambdaQuery()
                .eq(MqOutbox::getBizKey, orderNo)
                .eq(MqOutbox::getTopic, "baiyishop-order-auto-receive"));
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getDeliverAt()).isEqualTo(order.getAutoReceiveAt());

        // 客服角色只能查询与备注，发货一律 403（R5-Q3）
        String serviceToken = tokenProvider.createAccessToken(ID_BASE + 1, Audience.ADMIN, "SERVICE");
        assertThat(send("POST", "/api/v1/admin/orders/" + orderNo + "/ship",
                "{\"trackingNo\":\"SF999\"}", serviceToken, null).json().get("code").asInt()).isEqualTo(10003);
        assertThat(send("GET", "/api/v1/admin/orders?page=1&size=5", null, serviceToken, null)
                .json().get("code").asInt()).isZero();
    }

    @Test
    @DisplayName("支付成功：订单转待发货并扣减库存，重复事件只扣一次（REQ-802-3）")
    void paymentSuccessMovesOrderAndDeductsStock() throws Exception {
        long skuId = newSku(1500, 10);
        long addressId = newAddress();
        String orderNo = buyNow(skuId, 2, addressId, requestId()).json().get("data").get("orderNo").asString();

        orderService.markPaid(orderNo, "ALIPAY", "ALI-" + orderNo);
        orderService.markPaid(orderNo, "ALIPAY", "ALI-" + orderNo);

        Order order = orderOf(orderNo);
        assertThat(order.getStatus()).isEqualTo("PENDING_SHIPMENT");
        assertThat(order.getPayType()).isEqualTo("ALIPAY");
        assertThat(order.getPayTime()).isNotNull();
        assertThat(deductCalls.get()).isEqualTo(1);
        assertThat(statusLogMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                        .eq(OrderStatusLog::getOrderNo, orderNo).orderByAsc(OrderStatusLog::getId)).get(1)
                .getToStatus()).isEqualTo("PENDING_SHIPMENT");
    }

    @Test
    @DisplayName("已取消订单收到支付成功：不扣库存不改状态，留 WARN 供人工退款")
    void paymentAfterCancelIsIgnored() throws Exception {
        long skuId = newSku(1600, 10);
        long addressId = newAddress();
        String orderNo = buyNow(skuId, 1, addressId, requestId()).json().get("data").get("orderNo").asString();
        send("PUT", "/api/v1/orders/" + orderNo + "/cancel", null, userToken, null);

        orderService.markPaid(orderNo, "WECHAT", "WX-LATE-" + orderNo);

        assertThat(orderOf(orderNo).getStatus()).isEqualTo("CANCELLED");
        assertThat(deductCalls.get()).isZero();
    }

    @Test
    @DisplayName("订单可支付性内部接口：回传归属 / 状态 / 金额 / 超时时间（REQ-801）")
    void payableEndpointExposesOrderState() throws Exception {
        long skuId = newSku(1700, 10);
        long addressId = newAddress();
        String orderNo = buyNow(skuId, 2, addressId, requestId()).json().get("data").get("orderNo").asString();

        JsonNode data = send("GET", "/internal/orders/" + orderNo + "/payable", null, null, null)
                .json().get("data");
        assertThat(data.get("orderNo").asString()).isEqualTo(orderNo);
        assertThat(data.get("userId").asLong()).isEqualTo(userId);
        assertThat(data.get("status").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(data.get("payAmount").asLong()).isEqualTo(3400);
        assertThat(data.get("timeoutAt").asString()).isNotBlank();

        assertThat(send("GET", "/internal/orders/NOT-EXIST/payable", null, null, null)
                .json().get("code").asInt()).isEqualTo(50001);
    }

    @Test
    @DisplayName("我的订单列表按状态筛选；他人订单不可见也不可操作（REQ-702）")
    void orderListAndIsolation() throws Exception {
        long skuId = newSku(1200, 10);
        long addressId = newAddress();
        String orderNo = buyNow(skuId, 3, addressId, requestId()).json().get("data").get("orderNo").asString();

        JsonNode page = send("GET", "/api/v1/orders?status=ALL&page=1&size=10", null, userToken, null)
                .json().get("data");
        assertThat(page.get("total").asLong()).isEqualTo(1);
        assertThat(page.get("list").get(0).get("orderNo").asString()).isEqualTo(orderNo);
        assertThat(page.get("list").get(0).get("statusLabel").asString()).isEqualTo("待付款");
        assertThat(page.get("list").get(0).get("totalQuantity").asInt()).isEqualTo(3);

        JsonNode paid = send("GET", "/api/v1/orders?status=PENDING_SHIPMENT", null, userToken, null)
                .json().get("data");
        assertThat(paid.get("total").asLong()).isZero();

        JsonNode detail = send("GET", "/api/v1/orders/" + orderNo, null, userToken, null).json().get("data");
        assertThat(detail.get("receiver").get("phone").asString()).isEqualTo("13800000000");
        assertThat(detail.get("items").size()).isEqualTo(1);
        assertThat(detail.get("statusLogs").size()).isEqualTo(1);

        String otherUser = tokenProvider.createAccessToken(USER_SEQ.incrementAndGet(), Audience.USER, null);
        assertThat(send("GET", "/api/v1/orders/" + orderNo, null, otherUser, null)
                .json().get("code").asInt()).isEqualTo(50001);
        assertThat(send("PUT", "/api/v1/orders/" + orderNo + "/cancel", null, otherUser, null)
                .json().get("code").asInt()).isEqualTo(50001);
        assertThat(releaseCalls.get()).isZero();
    }

    @Test
    @DisplayName("后台备注：留存备注人姓名快照；订单不存在返回 50001")
    void adminNotes() throws Exception {
        long skuId = newSku(1300, 10);
        long addressId = newAddress();
        String orderNo = buyNow(skuId, 1, addressId, requestId()).json().get("data").get("orderNo").asString();

        assertThat(send("POST", "/api/v1/admin/orders/" + orderNo + "/notes",
                "{\"content\":\"客户要求改地址\"}", adminToken, null).json().get("data")
                .get("adminName").asString()).isEqualTo("测试管理员");

        JsonNode notes = send("GET", "/api/v1/admin/orders/" + orderNo + "/notes", null, adminToken, null)
                .json().get("data");
        assertThat(notes.size()).isEqualTo(1);
        assertThat(notes.get(0).get("content").asString()).isEqualTo("客户要求改地址");

        JsonNode adminDetail = send("GET", "/api/v1/admin/orders/" + orderNo, null, adminToken, null)
                .json().get("data");
        assertThat(adminDetail.get("userId").asLong()).isEqualTo(userId);
        assertThat(adminDetail.get("notes").size()).isEqualTo(1);

        assertThat(send("GET", "/api/v1/admin/orders/NOT_EXIST", null, adminToken, null)
                .json().get("code").asInt()).isEqualTo(50001);
        assertThat(send("GET", "/api/v1/orders?status=UNKNOWN", null, userToken, null)
                .json().get("code").asInt()).isEqualTo(10001);
        assertThat(lockCalls.get()).isEqualTo(1);
    }
}
