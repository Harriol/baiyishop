package com.harriol.baiyishop.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 支付域配置（REQ-801 ~ REQ-803）。
 *
 * @param mockSecret      模拟渠道签名密钥；真实渠道的密钥走密钥管理，不入库、不入日志（REQ-802-5）
 * @param mockPayEnabled  是否开放模拟渠道的「一键支付」入口（仅本地开发开）
 * @param outboxMaxRetry  支付成功事件投递的最大重试次数
 * @param paymentSuccessTopic 支付成功事件 topic，order-service 消费
 */
@ConfigurationProperties(prefix = "baiyishop.payment")
public record PaymentProperties(
        @DefaultValue("BaiyiMockChannelSecret2026") String mockSecret,
        @DefaultValue("true") boolean mockPayEnabled,
        @DefaultValue("10") int outboxMaxRetry,
        @DefaultValue("baiyishop-payment-success") String paymentSuccessTopic) {
}
