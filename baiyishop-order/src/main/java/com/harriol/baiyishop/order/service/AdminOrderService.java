package com.harriol.baiyishop.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.order.client.UserClient;
import com.harriol.baiyishop.order.config.OrderProperties;
import com.harriol.baiyishop.order.domain.OrderStatus;
import com.harriol.baiyishop.order.dto.AdminOrderItem;
import com.harriol.baiyishop.order.dto.OrderAutoReceiveEvent;
import com.harriol.baiyishop.order.dto.OrderNoteView;
import com.harriol.baiyishop.order.entity.Order;
import com.harriol.baiyishop.order.entity.OrderNote;
import com.harriol.baiyishop.order.entity.OrderStatusLog;
import com.harriol.baiyishop.order.mapper.OrderMapper;
import com.harriol.baiyishop.order.mapper.OrderNoteMapper;
import com.harriol.baiyishop.order.mapper.OrderStatusLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台订单管理（REQ-708）。
 * <p>超管 / 运营可发货，客服只读 + 备注（权限在 Controller 的 @RequiresRole 上，见 R5-Q3）。
 * <p>发货成功即在**同一本地事务**写入 7 天自动确认收货消息（REQ-707）：
 * 消息记漏的后果是订单一直挂在待收货，属可补偿但不应发生的问题。
 */
@Service
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    private static final String TAG_AUTO_RECEIVE = "ORDER_AUTO_RECEIVE";

    private final OrderMapper orderMapper;
    private final OrderNoteMapper noteMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final OrderOutboxService outboxService;
    private final UserClient userClient;
    private final OrderProperties properties;

    public AdminOrderService(OrderMapper orderMapper,
                             OrderNoteMapper noteMapper,
                             OrderStatusLogMapper statusLogMapper,
                             OrderOutboxService outboxService,
                             UserClient userClient,
                             OrderProperties properties) {
        this.orderMapper = orderMapper;
        this.noteMapper = noteMapper;
        this.statusLogMapper = statusLogMapper;
        this.outboxService = outboxService;
        this.userClient = userClient;
        this.properties = properties;
    }

    /** 订单列表：订单号 / 状态 / 用户 / 下单时间区间 */
    public PageResult<AdminOrderItem> page(long page, long size, String orderNo, String status,
                                           Long userId, LocalDateTime from, LocalDateTime to) {
        // ALL / 空值都表示不过滤；注意不能把 upperStatus(status) 直接写进 eq 的参数里：
        // 那样即使条件为 false 也会先求值，"ALL" 会被当成非法状态码抛错
        String statusFilter = !StringUtils.hasText(status) || "ALL".equalsIgnoreCase(status)
                ? null : OrderStatus.of(status.toUpperCase()).name();
        Page<Order> result = orderMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                Wrappers.<Order>lambdaQuery()
                        .like(StringUtils.hasText(orderNo), Order::getOrderNo, orderNo)
                        .eq(statusFilter != null, Order::getStatus, statusFilter)
                        .eq(userId != null, Order::getUserId, userId)
                        .ge(from != null, Order::getCreatedAt, from)
                        .le(to != null, Order::getCreatedAt, to)
                        .orderByDesc(Order::getId));
        List<AdminOrderItem> items = result.getRecords().stream()
                .map(order -> new AdminOrderItem(order.getOrderNo(), order.getUserId(), order.getStatus(),
                        OrderStatus.of(order.getStatus()).label(), order.getPayAmount(),
                        order.getReceiverName(), order.getReceiverPhone(), order.getTrackingNo(),
                        order.getCreatedAt(), order.getShipTime()))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    public Order require(String orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 发货：仅待发货可发货；成功即安排 7 天后自动确认收货（REQ-708、REQ-707） */
    @Transactional(rollbackFor = Exception.class)
    public void ship(String orderNo, String trackingNo, long adminId) {
        Order order = require(orderNo);
        if (!OrderStatus.of(order.getStatus()).shippable()) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime autoReceiveAt = now.plus(properties.autoReceiveAfter());
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_SHIPMENT.name())
                .set(Order::getStatus, OrderStatus.PENDING_RECEIPT.name())
                .set(Order::getShipTime, now)
                .set(Order::getTrackingNo, trackingNo)
                .set(Order::getAutoReceiveAt, autoReceiveAt));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        }
        writeStatusLog(order, OrderStatus.PENDING_SHIPMENT, OrderStatus.PENDING_RECEIPT,
                OrderStatusLog.OPERATOR_ADMIN, adminId, "发货，运单号 " + trackingNo);
        outboxService.append(properties.orderAutoReceiveTopic(), TAG_AUTO_RECEIVE, orderNo,
                new OrderAutoReceiveEvent(orderNo), autoReceiveAt);
        log.info("订单发货 orderNo={} trackingNo={} adminId={}", orderNo, trackingNo, adminId);
    }

    public List<OrderNoteView> notes(String orderNo) {
        require(orderNo);
        return noteMapper.selectList(Wrappers.<OrderNote>lambdaQuery()
                        .eq(OrderNote::getOrderId, require(orderNo).getId())
                        .orderByAsc(OrderNote::getId))
                .stream().map(OrderNoteView::from).toList();
    }

    /** 添加备注：客服唯一的写权限（R5-Q3） */
    @Transactional(rollbackFor = Exception.class)
    public OrderNoteView addNote(String orderNo, String content, long adminId) {
        Order order = require(orderNo);
        OrderNote note = new OrderNote();
        note.setOrderId(order.getId());
        note.setAdminId(adminId);
        note.setAdminName(userClient.adminName(adminId));
        note.setContent(content);
        noteMapper.insert(note);
        return OrderNoteView.from(note);
    }

    private void writeStatusLog(Order order, OrderStatus from, OrderStatus to,
                                String operatorType, Long operatorId, String reason) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setOrderNo(order.getOrderNo());
        log.setFromStatus(from.name());
        log.setToStatus(to.name());
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        statusLogMapper.insert(log);
    }
}
