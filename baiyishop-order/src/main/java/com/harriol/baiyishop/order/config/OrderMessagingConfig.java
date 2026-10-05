package com.harriol.baiyishop.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单域装配：延时消息的本地消息表投递 + 超时兜底扫描都需要定时任务支持。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrderProperties.class)
public class OrderMessagingConfig {
}
