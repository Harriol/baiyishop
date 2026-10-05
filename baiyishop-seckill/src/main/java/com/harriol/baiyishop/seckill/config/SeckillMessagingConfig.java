package com.harriol.baiyishop.seckill.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 秒杀域装配：本地消息投递与对账/补偿任务都需要定时任务支持。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SeckillProperties.class)
public class SeckillMessagingConfig {
}
