package com.harriol.baiyishop.user;

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
 * 收货地址接口端到端验证（REQ-103）。
 * <p>起真实 HTTP 端口，覆盖默认地址唯一、越权拒绝、手机号脱敏与参数校验。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AddressApiTests {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpClient http;
    private String base;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
    }

    private record Resp(int status, JsonNode json) {
    }

    private Resp send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), objectMapper.readTree(response.body()));
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
        return send(builder.build());
    }

    private String newUserToken() throws Exception {
        String username = "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Resp resp = send("POST", "/api/v1/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("accessToken").asString();
    }

    private String addressBody(String receiver, String phone, boolean isDefault) {
        return "{\"receiverName\":\"" + receiver + "\",\"receiverPhone\":\"" + phone + "\","
                + "\"province\":\"浙江省\",\"city\":\"杭州市\",\"district\":\"西湖区\","
                + "\"detail\":\"文三路 199 号\",\"isDefault\":" + isDefault + "}";
    }


    private record LoginInfo(String token, long userId) {
    }

    private LoginInfo newUser() throws Exception {
        String username = "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Resp resp = send("POST", "/api/v1/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
        return new LoginInfo(resp.json().get("data").get("accessToken").asString(),
                resp.json().get("data").get("user").get("id").asLong());
    }

    @Test
    @DisplayName("第一条地址自动成为默认地址，列表手机号脱敏")
    void firstAddressBecomesDefault() throws Exception {
        String token = newUserToken();
        Resp created = send("POST", "/api/v1/addresses", addressBody("李百益", "13800138888", false), token);
        assertThat(created.json().get("code").asInt()).isZero();
        assertThat(created.json().get("data").get("isDefault").asBoolean()).isTrue();

        Resp list = send("GET", "/api/v1/addresses", null, token);
        assertThat(list.json().get("code").asInt()).isZero();
        assertThat(list.json().get("data").size()).isEqualTo(1);
        assertThat(list.json().get("data").get(0).get("receiverPhone").asString()).isEqualTo("138****8888");
    }

    @Test
    @DisplayName("默认地址唯一：新增第二条默认时，第一条被取消")
    void onlyOneDefaultAddress() throws Exception {
        String token = newUserToken();
        long firstId = send("POST", "/api/v1/addresses", addressBody("甲", "13800138888", false), token)
                .json().get("data").get("id").asLong();
        long secondId = send("POST", "/api/v1/addresses", addressBody("乙", "13900139999", true), token)
                .json().get("data").get("id").asLong();

        Resp list = send("GET", "/api/v1/addresses", null, token);
        JsonNode data = list.json().get("data");
        assertThat(data.size()).isEqualTo(2);
        // 默认地址排首位
        assertThat(data.get(0).get("id").asLong()).isEqualTo(secondId);
        assertThat(data.get(0).get("isDefault").asBoolean()).isTrue();

        long defaultCount = 0;
        for (JsonNode node : data) {
            if (node.get("isDefault").asBoolean()) {
                defaultCount++;
            }
        }
        assertThat(defaultCount).isEqualTo(1);
        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    @DisplayName("设为默认地址接口生效")
    void setDefaultSwitchesFlag() throws Exception {
        String token = newUserToken();
        long firstId = send("POST", "/api/v1/addresses", addressBody("甲", "13800138888", false), token)
                .json().get("data").get("id").asLong();
        long secondId = send("POST", "/api/v1/addresses", addressBody("乙", "13900139999", false), token)
                .json().get("data").get("id").asLong();

        Resp switched = send("PUT", "/api/v1/addresses/" + firstId + "/default", null, token);
        assertThat(switched.json().get("code").asInt()).isZero();
        assertThat(switched.json().get("data").get("isDefault").asBoolean()).isTrue();

        Resp list = send("GET", "/api/v1/addresses", null, token);
        JsonNode data = list.json().get("data");
        assertThat(data.get(0).get("id").asLong()).isEqualTo(firstId);
        long defaultCount = 0;
        for (JsonNode node : data) {
            if (node.get("isDefault").asBoolean()) {
                defaultCount++;
            }
        }
        assertThat(defaultCount).isEqualTo(1);
        assertThat(secondId).isNotEqualTo(firstId);
    }

    @Test
    @DisplayName("修改地址内容生效")
    void updateAddress() throws Exception {
        String token = newUserToken();
        long id = send("POST", "/api/v1/addresses", addressBody("甲", "13800138888", false), token)
                .json().get("data").get("id").asLong();

        Resp updated = send("PUT", "/api/v1/addresses/" + id, addressBody("丙", "13700137777", false), token);
        assertThat(updated.json().get("code").asInt()).isZero();
        assertThat(updated.json().get("data").get("receiverName").asString()).isEqualTo("丙");

        Resp list = send("GET", "/api/v1/addresses", null, token);
        assertThat(list.json().get("data").get(0).get("receiverName").asString()).isEqualTo("丙");
    }

    @Test
    @DisplayName("删除默认地址后，剩余最新一条自动成为默认")
    void deleteDefaultPromotesAnother() throws Exception {
        String token = newUserToken();
        long firstId = send("POST", "/api/v1/addresses", addressBody("甲", "13800138888", false), token)
                .json().get("data").get("id").asLong();
        long secondId = send("POST", "/api/v1/addresses", addressBody("乙", "13900139999", false), token)
                .json().get("data").get("id").asLong();

        // 把第一条设为默认再删除
        send("PUT", "/api/v1/addresses/" + firstId + "/default", null, token);
        Resp deleted = send("DELETE", "/api/v1/addresses/" + firstId, null, token);
        assertThat(deleted.json().get("code").asInt()).isZero();

        Resp list = send("GET", "/api/v1/addresses", null, token);
        JsonNode data = list.json().get("data");
        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).get("id").asLong()).isEqualTo(secondId);
        assertThat(data.get(0).get("isDefault").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("越权：操作他人地址返回 20007，且不泄露资源是否存在")
    void cannotTouchOthersAddress() throws Exception {
        LoginInfo owner = newUser();
        LoginInfo other = newUser();

        long id = send("POST", "/api/v1/addresses", addressBody("甲", "13800138888", false), owner.token())
                .json().get("data").get("id").asLong();

        // 他人读不到（列表为空）
        Resp list = send("GET", "/api/v1/addresses", null, other.token());
        assertThat(list.json().get("data").size()).isZero();

        // 他人改 / 删 / 设默认都被拒
        assertThat(send("PUT", "/api/v1/addresses/" + id, addressBody("黑客", "13000130000", false), other.token())
                .json().get("code").asInt()).isEqualTo(20007);
        assertThat(send("DELETE", "/api/v1/addresses/" + id, null, other.token())
                .json().get("code").asInt()).isEqualTo(20007);
        assertThat(send("PUT", "/api/v1/addresses/" + id + "/default", null, other.token())
                .json().get("code").asInt()).isEqualTo(20007);

        // 原主人的地址未被改动
        Resp ownerList = send("GET", "/api/v1/addresses", null, owner.token());
        assertThat(ownerList.json().get("data").get(0).get("receiverName").asString()).isEqualTo("甲");
    }

    @Test
    @DisplayName("未登录访问地址接口返回 401")
    void anonymousRejected() throws Exception {
        assertThat(send("GET", "/api/v1/addresses", null, null).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("手机号格式非法返回 400 + 10001")
    void invalidPhoneRejected() throws Exception {
        String token = newUserToken();
        Resp resp = send("POST", "/api/v1/addresses", addressBody("甲", "12345", false), token);
        assertThat(resp.status()).isEqualTo(400);
        assertThat(resp.json().get("code").asInt()).isEqualTo(10001);
    }
}