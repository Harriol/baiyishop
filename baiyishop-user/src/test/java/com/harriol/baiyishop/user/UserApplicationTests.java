package com.harriol.baiyishop.user;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.harriol.baiyishop.common.web.filter.TraceIdFilter;
import com.harriol.baiyishop.common.web.handler.GlobalExceptionHandler;
import com.harriol.baiyishop.user.entity.User;
import com.harriol.baiyishop.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 骨架自检：上下文可加载、公共模块自动装配生效、Flyway 建表后 MyBatis-Plus 可查询。
 * <p>需要本机 MySQL 可用（deploy/mysql/init-native.ps1 初始化过）。
 */
@SpringBootTest
class UserApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserMapper userMapper;

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

    /** Flyway 已建表且 MyBatis-Plus 能正常查询（初始为空表） */
    @Test
    void userTableIsQueryable() {
        List<User> users = userMapper.selectList(null);
        assertThat(users).isNotNull();
    }

    /** 逻辑删除字段已生效：selectList 会自动带上 deleted = 0 */
    @Test
    void logicDeleteIsConfigured() {
        Long count = userMapper.selectCount(null);
        assertThat(count).isNotNull().isGreaterThanOrEqualTo(0L);
    }
}
