package com.harriol.baiyishop.user.wechat;

/**
 * 微信授权客户端。正式实现调用微信接口用 code 换 openid；
 * 本地开发用 {@link MockWechatAuthClient} 兜底（需求 2.3 允许模拟 openid）。
 */
public interface WechatAuthClient {

    /**
     * @param code 小程序 wx.login 返回的 code
     * @return openid 与可选 unionid
     */
    WechatSession exchange(String code);

    record WechatSession(String openid, String unionid) {
    }
}
