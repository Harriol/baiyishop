package com.harriol.baiyishop.order;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.order.client.InventoryClient;
import com.harriol.baiyishop.order.client.ProductClient;
import com.harriol.baiyishop.order.dto.SkuSnapshot;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * 购物车端到端验证（REQ-601、REQ-602）。
 * <p>商品 / 库存两个跨服务依赖用 Mockito 替身顶掉：本用例只验证购物车自身的规则
 * （累加、限购回滚、失效标记、合计试算、越权访问），跨服务真实链路由冒烟测试覆盖。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartApiTests {

    /**
     * 每次运行换一批 id：购物车是持久表，固定 id 会让上一轮运行的数据串进来
     * （表现为「累加了两次」「条目多出来」这类假失败）。
     */
    private static final long ID_BASE = 1_000_000_000L + System.currentTimeMillis() % 100_000_000L;
    private static final AtomicLong USER_SEQ = new AtomicLong(ID_BASE);
    private static final AtomicLong SKU_SEQ = new AtomicLong(ID_BASE);
    private static final int MAX_QUANTITY = 99;

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
    }

    @Autowired
    private ProductClient productClient;

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private HttpClient http;
    private String base;
    private String userToken;
    private final Map<Long, SkuSnapshot> snapshots = new HashMap<>();
    private final Map<Long, Integer> stock = new HashMap<>();

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
        userToken = tokenProvider.createAccessToken(USER_SEQ.incrementAndGet(), Audience.USER, null);
        snapshots.clear();
        stock.clear();

        // 用 doAnswer 而不是 when(...)：mock 实例在同一个上下文里跨用例复用，
        // when(mock.sku(...)) 会先真调一次方法，命中上一个用例留下的答案而抛错
        doAnswer(invocation -> {
            Long skuId = invocation.getArgument(0);
            SkuSnapshot snapshot = snapshots.get(skuId);
            if (snapshot == null) {
                throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            return snapshot;
        }).when(productClient).sku(anyLong());
        doAnswer(invocation -> {
            Map<Long, SkuSnapshot> found = new HashMap<>();
            snapshots.forEach((skuId, snapshot) -> found.put(skuId, snapshot));
            return found;
        }).when(productClient).skus(any());
        doAnswer(invocation -> new HashMap<>(stock)).when(inventoryClient).availableBatch(any());
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

    private long newSku(long productId, long price, boolean skuEnabled, String productStatus, int available) {
        long skuId = SKU_SEQ.incrementAndGet();
        snapshots.put(skuId, new SkuSnapshot(skuId, productId + "-01", "默认规格", price,
                "https://minio/sku-" + skuId + ".jpg", skuEnabled, productId, "商品" + productId,
                "https://minio/p" + productId + ".jpg", productStatus));
        stock.put(skuId, available);
        return skuId;
    }

    private long sellableSku(long price, int available) {
        return newSku(SKU_SEQ.get() + 1000, price, true, "ON_SALE", available);
    }

    private JsonNode cart(String token) throws Exception {
        Resp resp = send("GET", "/api/v1/carts", null, token);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data");
    }

    private JsonNode itemOf(JsonNode cart, long skuId) {
        for (JsonNode item : cart.get("items")) {
            if (item.get("skuId").asLong() == skuId) {
                return item;
            }
        }
        return null;
    }

    private JsonNode add(long skuId, int quantity, String token) throws Exception {
        return send("POST", "/api/v1/carts/items",
                "{\"skuId\":" + skuId + ",\"quantity\":" + quantity + "}", token).json();
    }

    @Test
    @DisplayName("未登录不能访问购物车；登录后空车返回空列表与 0 合计")
    void requiresLogin() throws Exception {
        assertThat(send("GET", "/api/v1/carts", null, null).json().get("code").asInt()).isEqualTo(10002);
        JsonNode cart = cart(userToken);
        assertThat(cart.get("items").size()).isZero();
        assertThat(cart.get("checkedAmount").asLong()).isZero();
        assertThat(cart.get("checkedCount").asInt()).isZero();
    }

    @Test
    @DisplayName("同一 SKU 重复加入为数量累加，合计按服务端单价计算")
    void addAccumulatesSameSku() throws Exception {
        long skuId = sellableSku(9900, 100);

        assertThat(add(skuId, 2, userToken).get("code").asInt()).isZero();
        JsonNode cart = add(skuId, 3, userToken).get("data");

        JsonNode item = itemOf(cart, skuId);
        assertThat(item.get("quantity").asInt()).isEqualTo(5);
        assertThat(item.get("price").asLong()).isEqualTo(9900);
        assertThat(item.get("name").asString()).startsWith("商品");
        assertThat(item.get("invalid").asBoolean()).isFalse();
        assertThat(cart.get("checkedAmount").asLong()).isEqualTo(9900L * 5);
        assertThat(cart.get("checkedCount").asInt()).isEqualTo(5);
        assertThat(cart.get("items").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("下架商品不能加入购物车（30008）")
    void addRejectsOffSaleProduct() throws Exception {
        long skuId = newSku(700_001L, 5000, true, "OFF_SALE", 100);
        assertThat(add(skuId, 1, userToken).get("code").asInt()).isEqualTo(30008);
        assertThat(cart(userToken).get("items").size()).isZero();
    }

    @Test
    @DisplayName("累加后超出单品限购：返回 50006 且本次累加回滚（REQ-601）")
    void addBeyondLimitRollsBack() throws Exception {
        long skuId = sellableSku(1000, 1000);
        add(skuId, 60, userToken);

        JsonNode exceeded = add(skuId, 50, userToken);
        assertThat(exceeded.get("code").asInt()).isEqualTo(50006);

        // 回滚生效：数量仍是 60，不是 110
        JsonNode item = itemOf(cart(userToken), skuId);
        assertThat(item.get("quantity").asInt()).isEqualTo(60);
    }

    @Test
    @DisplayName("失效商品置灰并给出原因，且不参与合计（REQ-602）")
    void listMarksInvalidItems() throws Exception {
        long normal = sellableSku(1000, 10);
        long soldOut = sellableSku(2000, 0);
        long shortStock = sellableSku(3000, 1);
        long offSale = sellableSku(4000, 100);
        long deleted = sellableSku(5000, 100);

        add(normal, 2, userToken);
        add(soldOut, 1, userToken);
        add(shortStock, 3, userToken);
        add(offSale, 1, userToken);
        add(deleted, 1, userToken);
        // 加车之后商品状态才变化：一个下架、一个被删除 —— 条目留在车里并给出原因
        snapshots.put(offSale, withStatus(snapshots.get(offSale), "OFF_SALE"));
        snapshots.remove(deleted);
        JsonNode cart = cart(userToken);

        assertThat(itemOf(cart, normal).get("invalid").asBoolean()).isFalse();
        assertThat(itemOf(cart, soldOut).get("invalidReason").asString()).isEqualTo("已售罄");
        assertThat(itemOf(cart, shortStock).get("invalidReason").asString()).isEqualTo("库存不足（剩 1 件）");
        assertThat(itemOf(cart, offSale).get("invalidReason").asString()).isEqualTo("商品已下架");
        assertThat(itemOf(cart, deleted).get("invalidReason").asString()).isEqualTo("商品已删除");
        assertThat(cart.get("items").size()).isEqualTo(5);
        // 只有有效且勾选的条目计入合计
        assertThat(cart.get("checkedAmount").asLong()).isEqualTo(2000L);
        assertThat(cart.get("checkedCount").asInt()).isEqualTo(2);
    }

    private SkuSnapshot withStatus(SkuSnapshot snapshot, String productStatus) {
        return new SkuSnapshot(snapshot.skuId(), snapshot.skuCode(), snapshot.specName(), snapshot.price(),
                snapshot.image(), snapshot.skuEnabled(), snapshot.productId(), snapshot.productName(),
                snapshot.productImage(), productStatus);
    }

    @Test
    @DisplayName("修改数量 / 勾选、全选取消全选、删除条目")
    void updateCheckAllAndDelete() throws Exception {
        long first = sellableSku(1000, 100);
        long second = sellableSku(2000, 100);
        add(first, 1, userToken);
        JsonNode cart = add(second, 1, userToken).get("data");
        long firstItemId = itemOf(cart, first).get("id").asLong();
        long secondItemId = itemOf(cart, second).get("id").asLong();

        // 改数量
        JsonNode updated = send("PUT", "/api/v1/carts/items/" + firstItemId, "{\"quantity\":7}", userToken)
                .json().get("data");
        assertThat(itemOf(updated, first).get("quantity").asInt()).isEqualTo(7);
        assertThat(updated.get("checkedAmount").asLong()).isEqualTo(1000L * 7 + 2000L);

        // 超限改数量被拒且不改动
        assertThat(send("PUT", "/api/v1/carts/items/" + firstItemId,
                "{\"quantity\":" + (MAX_QUANTITY + 1) + "}", userToken).json().get("code").asInt()).isEqualTo(50006);
        assertThat(itemOf(cart(userToken), first).get("quantity").asInt()).isEqualTo(7);

        // 取消勾选后不参与合计
        JsonNode unchecked = send("PUT", "/api/v1/carts/items/" + firstItemId, "{\"checked\":false}", userToken)
                .json().get("data");
        assertThat(unchecked.get("checkedAmount").asLong()).isEqualTo(2000L);
        assertThat(unchecked.get("checkedCount").asInt()).isEqualTo(1);

        // 取消全选 → 合计归零；全选 → 全部计入
        assertThat(send("PUT", "/api/v1/carts/checked", "{\"checked\":false}", userToken).json()
                .get("data").get("checkedAmount").asLong()).isZero();
        JsonNode allChecked = send("PUT", "/api/v1/carts/checked", "{\"checked\":true}", userToken)
                .json().get("data");
        assertThat(allChecked.get("checkedCount").asInt()).isEqualTo(8);

        // 删除条目
        JsonNode afterDelete = send("DELETE", "/api/v1/carts/items/" + secondItemId, null, userToken)
                .json().get("data");
        assertThat(afterDelete.get("items").size()).isEqualTo(1);
        assertThat(itemOf(afterDelete, second)).isNull();
    }

    @Test
    @DisplayName("不能操作他人的购物车条目（50007）")
    void cannotTouchOthersItem() throws Exception {
        long skuId = sellableSku(1000, 100);
        long itemId = itemOf(add(skuId, 1, userToken).get("data"), skuId).get("id").asLong();

        String otherUser = tokenProvider.createAccessToken(USER_SEQ.incrementAndGet(), Audience.USER, null);
        assertThat(send("PUT", "/api/v1/carts/items/" + itemId, "{\"quantity\":2}", otherUser)
                .json().get("code").asInt()).isEqualTo(50007);
        assertThat(send("DELETE", "/api/v1/carts/items/" + itemId, null, otherUser)
                .json().get("code").asInt()).isEqualTo(50007);
        assertThat(cart(otherUser).get("items").size()).isZero();
        // 本人条目未被影响
        assertThat(itemOf(cart(userToken), skuId).get("quantity").asInt()).isEqualTo(1);
    }
}
