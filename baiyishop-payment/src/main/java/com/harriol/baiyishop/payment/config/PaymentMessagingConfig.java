package com.harriol.baiyishop.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 支付域装配：本地消息表的定时投递需要开启定时任务。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentMessagingConfig {
}
