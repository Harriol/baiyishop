package com.harriol.baiyishop.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtProperties;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.user.dto.AdminLoginRequest;
import com.harriol.baiyishop.user.dto.AdminLoginResponse;
import com.harriol.baiyishop.user.dto.AdminProfileResponse;
import com.harriol.baiyishop.user.entity.Admin;
import com.harriol.baiyishop.user.mapper.AdminMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 后台管理员认证（REQ-105、REQ-106）。
 * <p>与前台用户完全独立：管理员表不同、令牌受众不同（aud=admin）、签名密钥也不同，互不通用。
 * <p>登录失败不锁定账号（需求只对前台会员要求连续失败 5 次锁定），但账号停用后无法登录。
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    /** 多角色时写入令牌的角色优先级：超管 > 运营 > 客服 */
    private static final List<String> ROLE_PRIORITY = List.of("SUPER_ADMIN", "OPERATOR", "SERVICE");

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    public AdminAuthService(AdminMapper adminMapper,
                            PasswordEncoder passwordEncoder,
                            JwtTokenProvider tokenProvider,
                            JwtProperties jwtProperties) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        String username = request.username().trim();
        Admin admin = adminMapper.selectOne(Wrappers.<Admin>lambdaQuery().eq(Admin::getUsername, username));

        boolean credentialsOk = admin != null
                && admin.getPasswordHash() != null
                && passwordEncoder.matches(request.password(), admin.getPasswordHash());
        if (!credentialsOk) {
            log.warn("后台登录失败 username={}", username);
            throw new BizException(ErrorCode.ADMIN_CREDENTIALS_INVALID);
        }
        if (admin.getStatus() == null || admin.getStatus() != 1) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }

        admin.setLastLoginAt(LocalDateTime.now());
        adminMapper.updateById(admin);

        AdminProfileResponse profile = toProfile(admin);
        String role = primaryRole(profile.roles());
        String accessToken = tokenProvider.createAccessToken(admin.getId(), Audience.ADMIN, role);
        log.info("后台登录成功 adminId={} roles={}", admin.getId(), profile.roles());
        return new AdminLoginResponse(accessToken, jwtProperties.getAccessTtl().toSeconds(), profile);
    }

    public AdminProfileResponse profile(long adminId) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return toProfile(admin);
    }

    /** 管理员列表（仅超级管理员可访问，见 AdminAdminController） */
    public List<AdminProfileResponse> listAdmins() {
        return adminMapper.selectList(Wrappers.<Admin>lambdaQuery().orderByAsc(Admin::getId))
                .stream().map(this::toProfile).toList();
    }

    private AdminProfileResponse toProfile(Admin admin) {
        return new AdminProfileResponse(
                admin.getId(),
                admin.getUsername(),
                admin.getRealName(),
                adminMapper.selectRoleCodes(admin.getId()),
                adminMapper.selectPermissionCodes(admin.getId()));
    }

    private String primaryRole(List<String> roles) {
        return roles.stream()
                .min(Comparator.comparingInt(role -> {
                    int index = ROLE_PRIORITY.indexOf(role);
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .orElse("");
    }
}