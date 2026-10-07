package com.harriol.baiyishop.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.jwt.JwtProperties;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.common.security.jwt.TokenPayload;
import com.harriol.baiyishop.user.cache.RedisTokenBlacklistChecker;
import com.harriol.baiyishop.user.dto.LoginRequest;
import com.harriol.baiyishop.user.dto.RegisterRequest;
import com.harriol.baiyishop.user.dto.TokenResponse;
import com.harriol.baiyishop.user.dto.UpdateProfileRequest;
import com.harriol.baiyishop.user.dto.UserProfileResponse;
import com.harriol.baiyishop.user.dto.WechatLoginRequest;
import com.harriol.baiyishop.user.entity.User;
import com.harriol.baiyishop.user.entity.UserWechat;
import com.harriol.baiyishop.user.mapper.UserMapper;
import com.harriol.baiyishop.user.mapper.UserWechatMapper;
import com.harriol.baiyishop.user.wechat.WechatAuthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 账号认证（REQ-101、REQ-102、REQ-104）。
 * <ul>
 *   <li>密码只存 BCrypt 哈希，比对用 matches，库中与日志中都不出现明文</li>
 *   <li>连续失败 5 次锁定 10 分钟，计数存 Redis（不落库，docs/architecture.md 7.1）</li>
 *   <li>注销把 access token 的 jti 写入黑名单，剩余有效期内立即失效</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String LOGIN_FAIL_KEY_PREFIX = "baiyishop:user:login:fail:";
    private static final int MAX_LOGIN_FAIL = 5;
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(10);

    private final UserMapper userMapper;
    private final UserWechatMapper userWechatMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redis;
    private final WechatAuthClient wechatAuthClient;

    public AuthService(UserMapper userMapper,
                       UserWechatMapper userWechatMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       JwtProperties jwtProperties,
                       StringRedisTemplate redis,
                       WechatAuthClient wechatAuthClient) {
        this.userMapper = userMapper;
        this.userWechatMapper = userWechatMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.redis = redis;
        this.wechatAuthClient = wechatAuthClient;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (existsByUsername(username)) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank() ? username : request.nickname().trim());
        user.setStatus(1);
        userMapper.insert(user);
        log.info("新用户注册成功 userId={}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String username = request.username().trim();
        String failKey = LOGIN_FAIL_KEY_PREFIX + username;

        String fails = redis.opsForValue().get(failKey);
        if (fails != null && Integer.parseInt(fails) >= MAX_LOGIN_FAIL) {
            throw new BizException(ErrorCode.USER_ACCOUNT_LOCKED);
        }

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        boolean credentialsOk = user != null
                && user.getPasswordHash() != null
                && passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!credentialsOk) {
            Long count = redis.opsForValue().increment(failKey);
            redis.expire(failKey, LOGIN_LOCK_TTL);
            log.warn("登录失败 username={} 累计{}次", username, count);
            if (count != null && count >= MAX_LOGIN_FAIL) {
                throw new BizException(ErrorCode.USER_ACCOUNT_LOCKED);
            }
            throw new BizException(ErrorCode.USER_CREDENTIALS_INVALID);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        redis.delete(failKey);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse wechatLogin(WechatLoginRequest request) {
        WechatAuthClient.WechatSession session = wechatAuthClient.exchange(request.code());

        UserWechat binding = userWechatMapper.selectOne(
                Wrappers.<UserWechat>lambdaQuery().eq(UserWechat::getOpenid, session.openid()));

        User user;
        if (binding != null) {
            user = userMapper.selectById(binding.getUserId());
            if (user == null) {
                throw new BizException(ErrorCode.USER_WECHAT_AUTH_FAILED);
            }
        } else {
            user = new User();
            user.setUsername("wx_" + session.openid());
            user.setNickname(request.nickname() == null || request.nickname().isBlank() ? "微信用户" : request.nickname());
            user.setAvatar(request.avatar());
            user.setStatus(1);
            userMapper.insert(user);

            UserWechat created = new UserWechat();
            created.setUserId(user.getId());
            created.setOpenid(session.openid());
            created.setUnionid(session.unionid());
            created.setNickname(request.nickname());
            created.setAvatar(request.avatar());
            userWechatMapper.insert(created);
            log.info("微信首次登录自动注册 userId={}", user.getId());
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        TokenPayload payload = tokenProvider.parse(refreshToken, Audience.USER);
        if (!payload.isRefreshToken()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(payload.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return issueTokens(user);
    }

    /** 注销：把 access token 的 jti 写入黑名单，TTL 取其剩余有效期 */
    public void logout(String accessToken) {
        TokenPayload payload = tokenProvider.parse(accessToken, Audience.USER);
        Duration remaining = Duration.between(Instant.now(), payload.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        redis.opsForValue().set(RedisTokenBlacklistChecker.BLACKLIST_KEY_PREFIX + payload.tokenId(), "1", remaining);
        log.info("用户注销 userId={}", payload.userId());
    }

    public UserProfileResponse profile(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return UserProfileResponse.from(user);
    }

    /**
     * 修改个人资料（REQ-104）：只更新传入的字段，昵称与手机号都未传则视为无效请求。
     * <p>账号（username）不可改，手机号为空串表示解绑。
     */
    @Transactional
    public UserProfileResponse updateProfile(long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        boolean changed = false;
        if (request.nickname() != null) {
            String nickname = request.nickname().trim();
            if (nickname.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "昵称不能为空");
            }
            user.setNickname(nickname);
            changed = true;
        }
        if (request.avatar() != null) {
            String avatar = request.avatar().trim();
            user.setAvatar(avatar.isEmpty() ? null : avatar);
            changed = true;
        }
        if (request.phone() != null) {
            String phone = request.phone().trim();
            user.setPhone(phone.isEmpty() ? null : phone);
            changed = true;
        }
        if (!changed) {
            throw new BizException(ErrorCode.PARAM_INVALID, "没有需要修改的内容");
        }
        userMapper.updateById(user);
        log.info("用户资料已更新 userId={}", userId);
        return UserProfileResponse.from(user);
    }

    private boolean existsByUsername(String username) {
        Long count = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        return count != null && count > 0;
    }

    private TokenResponse issueTokens(User user) {
        String role = "";
        String access = tokenProvider.createAccessToken(user.getId(), Audience.USER, role);
        String refresh = tokenProvider.createRefreshToken(user.getId(), Audience.USER, role);
        return new TokenResponse(access, refresh, jwtProperties.getAccessTtl().toSeconds(), UserProfileResponse.from(user));
    }
}
