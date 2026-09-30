package com.harriol.baiyishop.common.data.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 公共数据层自动装配：服务引入 common-data 即获得分页、乐观锁、防全表更新与字段自动填充。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class CommonDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return new MybatisPlusConfig().mybatisPlusInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }
}
