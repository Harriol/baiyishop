package com.harriol.baiyishop.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 订单域配置（REQ-601、REQ-704、REQ-707）。
 *
 * @param maxQuantityPerSku 单品限购数量（购物车累加与下单数量都受它约束，REQ-601）
 * @param payTimeout        未支付自动取消时长，默认 15 分钟（REQ-704）
 * @param autoReceiveAfter  发货后自动确认收货时长，默认 7 天（REQ-707）
 * @param timeoutScanDelay   超时兜底扫描间隔（延时消息丢失时的第二道防线）
 * @param outboxMaxRetry    本地消息投递最大重试次数
 * @param orderTimeoutTopic 15 分钟未支付自动取消的延时消息 topic（REQ-704）
 * @param orderAutoReceiveTopic 发货 7 天后自动确认收货的延时消息 topic（REQ-707）
 */
@ConfigurationProperties(prefix = "baiyishop.order")
public record OrderProperties(
        @DefaultValue("99") int maxQuantityPerSku,
        @DefaultValue("15m") Duration payTimeout,
        @DefaultValue("7d") Duration autoReceiveAfter,
        @DefaultValue("1m") Duration timeoutScanDelay,
        @DefaultValue("10") int outboxMaxRetry,
        @DefaultValue("baiyishop-order-timeout") String orderTimeoutTopic,
        @DefaultValue("baiyishop-order-auto-receive") String orderAutoReceiveTopic) {
}
