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
 * 个人资料查询与修改端到端验证（REQ-104）。
 * <p>覆盖部分字段更新、手机号解绑与校验、昵称为空、未登录。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserProfileApiTests {

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

    private String newUserToken() throws Exception {
        String username = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Resp resp = send("POST", "/api/v1/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("accessToken").asString();
    }

    @Test
    @DisplayName("修改昵称与手机号后能查到；手机号在接口返回时脱敏")
    void updateNicknameAndPhone() throws Exception {
        String token = newUserToken();

        Resp updated = send("PUT", "/api/v1/users/me",
                "{\"nickname\":\"百益测试昵称\",\"phone\":\"13800138001\"}", token);
        assertThat(updated.json().get("code").asInt()).isZero();
        assertThat(updated.json().get("data").get("nickname").asString()).isEqualTo("百益测试昵称");
        assertThat(updated.json().get("data").get("phone").asString()).isEqualTo("138****8001");

        JsonNode me = send("GET", "/api/v1/users/me", null, token).json().get("data");
        assertThat(me.get("nickname").asString()).isEqualTo("百益测试昵称");
        assertThat(me.get("phone").asString()).isEqualTo("138****8001");
    }

    @Test
    @DisplayName("只传一个字段时其它字段不受影响；手机号传空串表示解绑")
    void partialUpdateAndUnbindPhone() throws Exception {
        String token = newUserToken();
        send("PUT", "/api/v1/users/me", "{\"nickname\":\"老王\",\"phone\":\"13900139001\"}", token);

        Resp onlyNickname = send("PUT", "/api/v1/users/me", "{\"nickname\":\"小王\"}", token);
        assertThat(onlyNickname.json().get("data").get("nickname").asString()).isEqualTo("小王");
        assertThat(onlyNickname.json().get("data").get("phone").asString()).isEqualTo("139****9001");

        Resp unbind = send("PUT", "/api/v1/users/me", "{\"phone\":\"\"}", token);
        assertThat(unbind.json().get("code").asInt()).isZero();
        assertThat(unbind.json().get("data").get("phone").isNull()).isTrue();
    }

    @Test
    @DisplayName("手机号格式错误 10001、昵称为空 10001、没有任何字段 10001")
    void validation() throws Exception {
        String token = newUserToken();
        assertThat(send("PUT", "/api/v1/users/me", "{\"phone\":\"12345\"}", token).json().get("code").asInt())
                .isEqualTo(10001);
        assertThat(send("PUT", "/api/v1/users/me", "{\"nickname\":\"  \"}", token).json().get("code").asInt())
                .isEqualTo(10001);
        assertThat(send("PUT", "/api/v1/users/me", "{}", token).json().get("code").asInt()).isEqualTo(10001);
    }

    @Test
    @DisplayName("未登录不能修改资料")
    void requiresLogin() throws Exception {
        assertThat(send("PUT", "/api/v1/users/me", "{\"nickname\":\"x\"}", null).status()).isEqualTo(401);
    }
}
