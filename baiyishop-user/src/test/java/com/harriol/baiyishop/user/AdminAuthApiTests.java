package com.harriol.baiyishop.user;

import com.harriol.baiyishop.user.entity.Admin;
import com.harriol.baiyishop.user.mapper.AdminMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * 后台认证与 RBAC 端到端验证（REQ-105、REQ-106）。
 * <p>覆盖：管理员登录、账号停用、后台令牌与用户令牌的受众隔离、
 * 以及「客服仅能查单备注、不可访问管理员模块」的角色边界。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthApiTests {

    private static final String PASSWORD = "Admin@123456";

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    /** 建一个指定角色的管理员，返回用户名 */
    private String createAdmin(String roleCode, int status) {
        String username = "adm" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRealName("测试-" + roleCode);
        admin.setStatus(status);
        adminMapper.insert(admin);

        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM `role` WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("INSERT INTO admin_role (admin_id, role_id) VALUES (?, ?)", admin.getId(), roleId);
        return username;
    }

    private String login(String username) throws Exception {
        Resp resp = send("POST", "/api/v1/admin/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(resp.json().get("code").asInt()).isZero();
        return resp.json().get("data").get("accessToken").asString();
    }

    @Test
    @DisplayName("超管登录成功，返回角色与权限码列表")
    void superAdminLoginReturnsProfile() throws Exception {
        String username = createAdmin("SUPER_ADMIN", 1);
        Resp resp = send("POST", "/api/v1/admin/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.json().get("code").asInt()).isZero();
        assertThat(resp.json().get("data").get("accessToken").asString()).isNotBlank();
        JsonNode admin = resp.json().get("data").get("admin");
        assertThat(admin.get("roles").get(0).asString()).isEqualTo("SUPER_ADMIN");
        // 超管拥有全部权限
        assertThat(admin.get("permissions").size()).isEqualTo(21);
    }

    @Test
    @DisplayName("后台密码错误返回 20008，停用账号返回 20009")
    void wrongPasswordAndDisabledAccount() throws Exception {
        String username = createAdmin("OPERATOR", 1);
        Resp wrong = send("POST", "/api/v1/admin/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"WrongPass1\"}", null);
        assertThat(wrong.json().get("code").asInt()).isEqualTo(20008);

        String disabled = createAdmin("OPERATOR", 0);
        Resp stopped = send("POST", "/api/v1/admin/auth/login",
                "{\"username\":\"" + disabled + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(stopped.json().get("code").asInt()).isEqualTo(20009);
    }

    @Test
    @DisplayName("后台接口需要后台令牌：无令牌 401，用户令牌也 401（受众隔离）")
    void adminEndpointsRejectAnonymousAndUserToken() throws Exception {
        assertThat(send("GET", "/api/v1/admin/auth/me", null, null).status()).isEqualTo(401);

        // 用前台用户令牌访问后台接口
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Resp registered = send("POST", "/api/v1/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}", null);
        String userToken = registered.json().get("data").get("accessToken").asString();
        assertThat(send("GET", "/api/v1/admin/auth/me", null, userToken).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("三种后台角色都可访问 /admin/auth/me")
    void allAdminRolesCanReadOwnProfile() throws Exception {
        for (String role : new String[]{"SUPER_ADMIN", "OPERATOR", "SERVICE"}) {
            String token = login(createAdmin(role, 1));
            Resp me = send("GET", "/api/v1/admin/auth/me", null, token);
            assertThat(me.status()).isEqualTo(200);
            assertThat(me.json().get("data").get("roles").get(0).asString()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("管理员模块仅超管可访问：运营与客服返回 403")
    void adminModuleIsSuperAdminOnly() throws Exception {
        String superToken = login(createAdmin("SUPER_ADMIN", 1));
        Resp allowed = send("GET", "/api/v1/admin/admins", null, superToken);
        assertThat(allowed.status()).isEqualTo(200);
        assertThat(allowed.json().get("code").asInt()).isZero();
        assertThat(allowed.json().get("data").size()).isGreaterThan(0);

        for (String role : new String[]{"OPERATOR", "SERVICE"}) {
            String token = login(createAdmin(role, 1));
            Resp denied = send("GET", "/api/v1/admin/admins", null, token);
            assertThat(denied.status()).isEqualTo(403);
            assertThat(denied.json().get("code").asInt()).isEqualTo(10003);
        }
    }

    @Test
    @DisplayName("客服角色的权限码只有订单查询与备注（R5-Q3）")
    void serviceRoleOnlyHasOrderPermissions() throws Exception {
        String token = login(createAdmin("SERVICE", 1));
        JsonNode me = send("GET", "/api/v1/admin/auth/me", null, token).json().get("data");
        assertThat(me.get("permissions").size()).isEqualTo(2);
        assertThat(me.get("permissions").get(0).asString()).isEqualTo("order:note");
        assertThat(me.get("permissions").get(1).asString()).isEqualTo("order:read");
    }
}