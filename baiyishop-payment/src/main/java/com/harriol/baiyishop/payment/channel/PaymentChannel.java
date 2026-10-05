package com.harriol.baiyishop.payment.channel;

import com.harriol.baiyishop.payment.entity.Payment;

import java.util.Map;

/**
 * 支付渠道策略（docs/architecture.md 5.2 要点 1，REQ-801）。
 * <p>本期注册的是**模拟实现**：支付参数与验签规则都按真实渠道的形状设计，
 * 将来接真渠道只需新增一个实现类 + 配置，业务代码不动。
 */
public interface PaymentChannel {

    /** 渠道码：WECHAT / ALIPAY */
    String code();

    /** 渠道支付参数（前端据此唤起支付） */
    Map<String, Object> payParams(Payment payment);

    /**
     * 验签并解析回调。
     *
     * @throws com.harriol.baiyishop.common.core.exception.BizException 验签失败抛 60004
     */
    ChannelCallback verify(String rawBody, String signature);
}
