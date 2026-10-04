package com.harriol.baiyishop.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台接口的角色校验（REQ-106、docs/architecture.md 9.2）。
 * <p>标注在 Controller 类或方法上，只有持后台令牌且角色在允许列表内的请求才能通过；
 * 客服（SERVICE）只能访问订单查询与备注，其余后台模块一律 403。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {

    /** 允许的角色码，对应 role.code：SUPER_ADMIN / OPERATOR / SERVICE */
    String[] value();
}