package com.harriol.baiyishop.common.web.client;

import org.springframework.http.HttpHeaders;

import java.lang.reflect.Method;

/**
 * 把 Seata 全局事务 XID 透传给内部调用（ADR-002、docs/architecture.md 4.1）。
 * <p>为什么需要：order-service 在全局事务里调用 inventory-service 的锁库存接口，
 * 只有把 XID 带过去，库存方的数据库写入才会加入同一全局事务、写出 undo_log，
 * 之后才能被 TC 回滚。不带这个头，跨服务写的「原子性」就只是纸面约定。
 * <p>公共模块不硬依赖 Seata（product / search / user 不参与全局事务），
 * 因此用反射访问 {@code RootContext}：没有 Seata 的服务静默跳过。
 */
final class SeataXidPropagator {

    /** 与 Seata 自带的 HTTP 集成保持一致的头名 */
    private static final String HEADER_XID = "TX_XID";
    private static final String HEADER_BRANCH_TYPE = "TX_BRANCH_TYPE";

    private static final Method GET_XID;
    private static final Method GET_BRANCH_TYPE;

    static {
        Method getXid = null;
        Method getBranchType = null;
        try {
            Class<?> rootContext = Class.forName("io.seata.core.context.RootContext");
            getXid = rootContext.getMethod("getXID");
            getBranchType = rootContext.getMethod("getBranchType");
        } catch (Throwable ignored) {
            // 未引入 Seata：保持 null，调用时直接跳过
        }
        GET_XID = getXid;
        GET_BRANCH_TYPE = getBranchType;
    }

    private SeataXidPropagator() {
    }

    static void propagate(HttpHeaders headers) {
        if (GET_XID == null) {
            return;
        }
        try {
            Object xid = GET_XID.invoke(null);
            if (xid instanceof String value && !value.isBlank()) {
                headers.set(HEADER_XID, value);
                Object branchType = GET_BRANCH_TYPE.invoke(null);
                if (branchType != null) {
                    headers.set(HEADER_BRANCH_TYPE, String.valueOf(branchType));
                }
            }
        } catch (Exception ignored) {
            // 反射失败不影响业务调用：退回「无全局事务」的语义，而不是让请求直接失败
        }
    }
}
