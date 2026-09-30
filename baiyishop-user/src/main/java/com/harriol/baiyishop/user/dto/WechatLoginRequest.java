package com.harriol.baiyishop.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信授权登录请求（REQ-102）。
 * <p>正式流程为：小程序调用 wx.login 拿到 code，后端用 code 换取 openid。
 * 本地开发未接微信测试号时，可用 mockEnabled 配置 + mockOpenid 走模拟通道。
 */
public record WechatLoginRequest(
        @NotBlank(message = "缺少微信授权 code") String code,
        String nickname,
        String avatar) {
}
