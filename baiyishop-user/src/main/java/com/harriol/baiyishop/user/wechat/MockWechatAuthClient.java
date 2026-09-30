package com.harriol.baiyishop.user.wechat;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟微信授权（本地开发默认启用）。
 * <p>把传入的 code 直接当作 openid，便于不依赖微信测试号也能联调登录链路。
 * <p>接入真实微信后，把 {@code baiyishop.wechat.mock-enabled} 置为 false 并切换到真实实现。
 */
@Component
@ConditionalOnProperty(name = "baiyishop.wechat.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockWechatAuthClient implements WechatAuthClient {

    private static final Logger log = LoggerFactory.getLogger(MockWechatAuthClient.class);

    @Override
    public WechatSession exchange(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.USER_WECHAT_AUTH_FAILED);
        }
        String openid = "mock_" + code.trim();
        log.warn("使用模拟微信授权登录，openid={}（生产环境必须关闭 baiyishop.wechat.mock-enabled）", openid);
        return new WechatSession(openid, null);
    }
}
