/**
 * 公共安全模块：JWT 生成与解析、密码加密、登录用户上下文、权限注解与拦截器。
 * <p>依据 docs/adr/ADR-003-鉴权方案.md 与 docs/architecture.md 第 9 章：
 * 网关统一校验 + 服务端二次校验；用户令牌与后台令牌使用不同 aud 与不同密钥。
 * <p>具体实现随「统一鉴权」任务落地。
 */
package com.harriol.baiyishop.common.security;
