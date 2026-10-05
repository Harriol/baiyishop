package com.harriol.baiyishop.seckill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 秒杀域配置（REQ-901 ~ REQ-906）。
 *
 * @param orderTopic           预扣成功后下单消息的 topic（order-service 消费）
 * @param cancelTopic          秒杀订单取消消息的 topic（本服务消费，回补秒杀池，REQ-905）
 * @param rateLimitPerSecond   单用户每秒请求上限（REQ-906 防刷）
 * @param queuedTimeoutMinutes 排队记录多久未落单由补偿任务兜底（ADR-008 失败补偿）
 * @param outboxMaxRetry       本地消息投递最大重试次数
 */
@ConfigurationProperties(prefix = "baiyishop.seckill")
public record SeckillProperties(
        @DefaultValue("baiyishop-seckill-order") String orderTopic,
        @DefaultValue("baiyishop-seckill-order-cancel") String cancelTopic,
        @DefaultValue("10") int rateLimitPerSecond,
        @DefaultValue("5") int queuedTimeoutMinutes,
        @DefaultValue("10") int outboxMaxRetry) {
}
