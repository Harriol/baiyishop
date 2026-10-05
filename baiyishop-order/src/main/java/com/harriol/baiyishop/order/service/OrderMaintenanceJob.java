package com.harriol.baiyishop.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.order.domain.OrderStatus;
import com.harriol.baiyishop.order.entity.Order;
import com.harriol.baiyishop.order.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时兜底扫描（docs/architecture.md 5.3「消息丢失兜底」）。
 * <p>延时消息是主路径，这里是第二道防线：每 {@code baiyishop.order.timeout-scan-delay}
 * 扫一遍「已超时仍待付款」与「发货超 7 天仍待收货」的订单补处理。
 * <p>扫描区间走索引（idx_status_timeout / idx_status_auto_receive），单批 100 条，
 * 单条失败只记日志不中断整批。
 */
@Component
public class OrderMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(OrderMaintenanceJob.class);

    private static final int BATCH_SIZE = 100;

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public OrderMaintenanceJob(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${baiyishop.order.timeout-scan-delay:1m}")
    public void compensate() {
        LocalDateTime now = LocalDateTime.now();
        scan(orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name())
                        .le(Order::getTimeoutAt, now)
                        .orderByAsc(Order::getId)
                        .last("LIMIT " + BATCH_SIZE)),
                Order::getOrderNo, orderService::cancelByTimeout, "超时取消");

        scan(orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .eq(Order::getStatus, OrderStatus.PENDING_RECEIPT.name())
                        .le(Order::getAutoReceiveAt, now)
                        .orderByAsc(Order::getId)
                        .last("LIMIT " + BATCH_SIZE)),
                Order::getOrderNo, orderService::autoReceive, "自动确认收货");
    }

    private void scan(List<Order> orders, java.util.function.Function<Order, String> keyOf,
                      java.util.function.Consumer<String> action, String actionName) {
        for (Order order : orders) {
            try {
                action.accept(keyOf.apply(order));
            } catch (Exception ex) {
                log.warn("兜底{}失败 orderNo={}", actionName, order.getOrderNo(), ex);
            }
        }
    }
}
