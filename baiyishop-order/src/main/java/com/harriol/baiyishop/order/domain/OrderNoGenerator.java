package com.harriol.baiyishop.order.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单号生成（REQ-701「全局唯一订单号」）。
 * <p>22 位 = 14 位时间（yyyyMMddHHmmss）+ 3 位用户尾号 + 5 位随机，
 * 既便于人工按时间定位，也把并发碰撞概率压到极低；唯一性最终由 {@code uk_order_no} 兜底，
 * 命中冲突时服务层重试生成（见 OrderCreator）。
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private OrderNoGenerator() {
    }

    public static String next(long userId) {
        return LocalDateTime.now().format(TIMESTAMP)
                + String.format("%03d", userId % 1000)
                + String.format("%05d", ThreadLocalRandom.current().nextInt(100000));
    }
}
