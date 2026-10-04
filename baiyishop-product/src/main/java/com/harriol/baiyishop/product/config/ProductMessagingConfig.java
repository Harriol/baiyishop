package com.harriol.baiyishop.product.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商品变更消息的装配（docs/adr/ADR-005）：
 * 本地消息表写入随业务事务，投递由定时任务完成，故需要开启定时任务支持。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ProductMqProperties.class)
public class ProductMessagingConfig {
}
