package com.harriol.baiyishop.user;

import com.harriol.baiyishop.common.web.filter.TraceIdFilter;
import com.harriol.baiyishop.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 骨架自检：不依赖任何外部中间件即可加载上下文，并验证 common-web 的自动装配已生效。
 */
@SpringBootTest
class UserApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void commonWebAutoConfigurationIsApplied() {
        assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
        assertThat(applicationContext.getBean(TraceIdFilter.class)).isNotNull();
    }
}
