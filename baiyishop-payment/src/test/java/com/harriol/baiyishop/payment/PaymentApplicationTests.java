package com.harriol.baiyishop.payment;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.harriol.baiyishop.common.web.filter.TraceIdFilter;
import com.harriol.baiyishop.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 骨架自检：上下文可加载、公共模块自动装配生效、Flyway 建表后数据源可查询。
 * <p>需要本机 MySQL 可用（deploy/mysql/init-native.ps1 初始化过）。
 */
@SpringBootTest
class PaymentApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void commonWebAutoConfigurationIsApplied() {
        assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
        assertThat(applicationContext.getBean(TraceIdFilter.class)).isNotNull();
    }

    @Test
    void commonDataAutoConfigurationIsApplied() {
        assertThat(applicationContext.getBean(MybatisPlusInterceptor.class)).isNotNull();
    }

    /** Flyway 已执行：迁移历史表存在且有成功记录 */
    @Test
    void flywayMigrationsApplied() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(applied).isNotNull().isGreaterThan(0);
    }

    /** 本服务的业务表已建好 */
    @Test
    void businessTablesExist() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tables).isNotNull().isGreaterThan(0);
    }
}
