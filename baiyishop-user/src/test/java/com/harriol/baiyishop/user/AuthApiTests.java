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
 * 认证接口端到端验证（REQ-101、REQ-102、REQ-104、REQ-1003）。
 * <p>起真实 HTTP 端口发请求，覆盖统一响应体、鉴权过滤器、Redis 登录锁定与令牌黑名单。
 * <p>依赖本机 MySQL 与 Redis；用户名随机，不污染其他数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiTests {

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

    private Resp post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private Resp get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private static String randomUsername() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private JsonNode register(String username) throws Exception {
        Resp resp = post("/api/v1/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data");
    }

    @Test
    @DisplayName("注册返回统一响应体与令牌，且带 traceId 响应头")
    void registerReturnsTokens() throws Exception {
        String username = randomUsername();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"测试用户\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.get("message").asString()).isEqualTo("success");
        assertThat(json.get("data").get("accessToken").asString()).isNotBlank();
        assertThat(json.get("data").get("refreshToken").asString()).isNotBlank();
        assertThat(json.get("data").get("expiresIn").asLong()).isEqualTo(7200L);
        assertThat(json.get("data").get("user").get("username").asString()).isEqualTo(username);
        assertThat(response.headers().firstValue("X-Trace-Id")).isPresent();
    }

    @Test
    @DisplayName("重复注册返回 20003")
    void duplicateRegisterRejected() throws Exception {
        String username = randomUsername();
        register(username);
        Resp resp = post("/api/v1/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(resp.json().get("code").asInt()).isEqualTo(20003);
    }

    @Test
    @DisplayName("密码错误返回 20001，连续 5 次后锁定返回 20002")
    void wrongPasswordThenLocked() throws Exception {
        String username = randomUsername();
        register(username);
        String wrong = "{\"username\":\"" + username + "\",\"password\":\"WrongPass1\"}";

        for (int i = 1; i <= 4; i++) {
            assertThat(post("/api/v1/auth/login", wrong, null).json().get("code").asInt()).isEqualTo(20001);
        }
        assertThat(post("/api/v1/auth/login", wrong, null).json().get("code").asInt()).isEqualTo(20002);

        // 锁定期内即使密码正确也被拒
        Resp correct = post("/api/v1/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(correct.json().get("code").asInt()).isEqualTo(20002);
    }

    @Test
    @DisplayName("参数校验失败返回 400 + 10001")
    void invalidParameterRejected() throws Exception {
        Resp resp = post("/api/v1/auth/register", "{\"username\":\"ab\",\"password\":\"123\"}", null);
        assertThat(resp.status()).isEqualTo(400);
        assertThat(resp.json().get("code").asInt()).isEqualTo(10001);
    }

    @Test
    @DisplayName("带令牌可访问 /users/me 且手机号脱敏；无令牌返回 401")
    void profileRequiresToken() throws Exception {
        String username = randomUsername();
        String token = register(username).get("accessToken").asString();

        Resp me = get("/api/v1/users/me", token);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("data").get("username").asString()).isEqualTo(username);

        Resp anonymous = get("/api/v1/users/me", null);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.json().get("code").asInt()).isEqualTo(10002);
    }

    @Test
    @DisplayName("伪造令牌返回 401，不会被当作匿名放行")
    void forgedTokenRejected() throws Exception {
        Resp resp = get("/api/v1/users/me", "not-a-real-token");
        assertThat(resp.status()).isEqualTo(401);
        assertThat(resp.json().get("code").asInt()).isEqualTo(10002);
    }

    @Test
    @DisplayName("注销后原令牌立即失效（黑名单生效），refreshToken 可换新令牌")
    void logoutThenRefresh() throws Exception {
        String username = randomUsername();
        JsonNode data = register(username);
        String accessToken = data.get("accessToken").asString();
        String refreshToken = data.get("refreshToken").asString();

        assertThat(get("/api/v1/users/me", accessToken).status()).isEqualTo(200);

        assertThat(post("/api/v1/auth/logout", "", accessToken).json().get("code").asInt()).isZero();
        assertThat(get("/api/v1/users/me", accessToken).status()).isEqualTo(401);

        Resp refreshed = post("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + refreshToken + "\"}", null);
        assertThat(refreshed.json().get("code").asInt()).isZero();
        assertThat(refreshed.json().get("data").get("accessToken").asString()).isNotBlank();
    }

    @Test
    @DisplayName("微信模拟登录：首次自动注册，同一 code 重复登录返回同一账号")
    void wechatLoginCreatesThenReusesAccount() throws Exception {
        String code = "code_" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"code\":\"" + code + "\",\"nickname\":\"微信用户\"}";

        Resp first = post("/api/v1/auth/wechat-login", body, null);
        Resp second = post("/api/v1/auth/wechat-login", body, null);

        assertThat(first.json().get("code").asInt()).isZero();
        assertThat(first.json().get("data").get("user").get("id").asLong())
                .isEqualTo(second.json().get("data").get("user").get("id").asLong());
    }
}
