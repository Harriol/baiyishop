/**
 * 公共消息模块：消息体基类、本地消息表（事务性发件箱）、消费幂等组件与死信处理。
 * <p>依据 docs/adr/ADR-002-跨服务一致性方案.md 与 docs/architecture.md 4.2、6.3：
 * 同步跨服务写由 Seata AT 保证原子；Redis / ES / MQ 靠「幂等 + 本地消息表 + 对账」收敛。
 */
package com.harriol.baiyishop.common.mq;
