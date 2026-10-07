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

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后台图片上传端到端验证（REQ-203）。
 * <p>真实打到 MinIO：上传后返回的地址必须能被直接访问（前台 &lt;img&gt; 就这么用）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUploadApiTests {

    /** 1x1 透明 PNG */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=");

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
        operatorToken = tokenProvider.createAccessToken(2001L, Audience.ADMIN, "OPERATOR");
        serviceToken = tokenProvider.createAccessToken(2002L, Audience.ADMIN, "SERVICE");
    }

    private record Resp(int status, JsonNode json) {
    }

    private Resp send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), objectMapper.readTree(response.body()));
    }

    /** 手工拼 multipart 请求体：只依赖 JDK，无需额外测试库 */
    private Resp upload(byte[] content, String filename, String contentType, String scene, String token) throws Exception {
        String boundary = "----baiyi" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (scene != null) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"scene\"\r\n\r\n" + scene + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/api/v1/admin/uploads/images"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    @Test
    @DisplayName("上传图片返回可访问地址，且能直接从对象存储取回")
    void uploadReturnsReachableUrl() throws Exception {
        Resp resp = upload(PNG, "a.png", "image/png", "product", operatorToken);
        assertThat(resp.json().get("code").asInt()).isZero();
        String url = resp.json().get("data").get("url").asString();
        assertThat(url).contains("/baiyishop/product/").endsWith(".png");

        HttpResponse<byte[]> fetched = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("轮播图场景进 banner 目录")
    void bannerSceneUsesBannerFolder() throws Exception {
        Resp resp = upload(PNG, "b.jpg", "image/jpeg", "banner", operatorToken);
        assertThat(resp.json().get("data").get("url").asString()).contains("/baiyishop/banner/");
    }

    @Test
    @DisplayName("非图片类型被拒（10007），空文件被拒（10001）")
    void rejectsNonImage() throws Exception {
        Resp text = upload("hello".getBytes(StandardCharsets.UTF_8), "a.txt", "text/plain", null, operatorToken);
        assertThat(text.json().get("code").asInt()).isEqualTo(10007);

        Resp empty = upload(new byte[0], "a.png", "image/png", null, operatorToken);
        assertThat(empty.json().get("code").asInt()).isEqualTo(10001);
    }

    @Test
    @DisplayName("角色校验：客服 403、无令牌 401")
    void roleEnforcement() throws Exception {
        assertThat(upload(PNG, "a.png", "image/png", null, serviceToken).status()).isEqualTo(403);
        assertThat(upload(PNG, "a.png", "image/png", null, null).status()).isEqualTo(401);
    }
}
