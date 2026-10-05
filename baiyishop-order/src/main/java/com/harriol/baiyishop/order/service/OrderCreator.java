package com.harriol.baiyishop.order.service;

import com.harriol.baiyishop.order.client.InventoryClient;
import com.harriol.baiyishop.order.config.OrderProperties;
import com.harriol.baiyishop.order.domain.OrderNoGenerator;
import com.harriol.baiyishop.order.domain.OrderStatus;
import com.harriol.baiyishop.order.dto.OrderCommand;
import com.harriol.baiyishop.order.dto.OrderLine;
import com.harriol.baiyishop.order.dto.OrderTimeoutEvent;
import com.harriol.baiyishop.order.entity.Order;
import com.harriol.baiyishop.order.entity.OrderItem;
import com.harriol.baiyishop.order.entity.OrderRequest;
import com.harriol.baiyishop.order.entity.OrderStatusLog;
import com.harriol.baiyishop.order.mapper.OrderItemMapper;
import com.harriol.baiyishop.order.mapper.OrderMapper;
import com.harriol.baiyishop.order.mapper.OrderRequestMapper;
import com.harriol.baiyishop.order.mapper.OrderStatusLogMapper;
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 下单的**写事务部分**（docs/architecture.md 5.1、ADR-002）。
 * <p>全局事务边界刻意收窄：商品 / 地址读取都在事务外（OrderService 完成），
 * 这里只做「锁库存 + 写订单」，事务内全是数据库操作，不做远程读、不发 MQ。
 * <p>任一步失败（库存不足、唯一键冲突等）都会让 Seata 依据各参与方的 undo_log
 * 回滚已锁定的库存，满足 REQ-701「全成功或全回滚」。
 */
@Service
public class OrderCreator {

    private static final Logger log = LoggerFactory.getLogger(OrderCreator.class);

    private static final String TAG_TIMEOUT = "ORDER_TIMEOUT";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final OrderRequestMapper orderRequestMapper;
    private final InventoryClient inventoryClient;
    private final OrderOutboxService outboxService;
    private final OrderProperties properties;

    public OrderCreator(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        OrderStatusLogMapper statusLogMapper,
                        OrderRequestMapper orderRequestMapper,
                        InventoryClient inventoryClient,
                        OrderOutboxService outboxService,
                        OrderProperties properties) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.orderRequestMapper = orderRequestMapper;
        this.inventoryClient = inventoryClient;
        this.outboxService = outboxService;
        this.properties = properties;
    }

    @GlobalTransactional(name = "baiyishop-order-create", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public Order create(OrderCommand command) {
        String orderNo = OrderNoGenerator.next(command.userId());

        // ① 预占库存：库存不足这里会抛 40001，全局事务回滚 → 不产生订单（REQ-701 约定 3）
        inventoryClient.lock(orderNo, command.lines());

        // ② 订单主表：金额由服务端按快照单价重算，全场包邮
        long totalAmount = command.lines().stream().mapToLong(OrderLine::amount).sum();
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(command.userId());
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setSource(command.source().name());
        order.setTotalAmount(totalAmount);
        order.setFreightAmount(0L);
        order.setPayAmount(totalAmount);
        order.setReceiverName(command.address().receiverName());
        order.setReceiverPhone(command.address().receiverPhone());
        order.setReceiverAddress(command.receiverAddress());
        order.setRemark(command.remark());
        order.setTimeoutAt(now.plus(properties.payTimeout()));
        orderMapper.insert(order);

        // ③ 明细快照
        for (OrderLine line : command.lines()) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setOrderNo(orderNo);
            item.setProductId(line.sku().productId());
            item.setSkuId(line.sku().skuId());
            item.setProductName(line.sku().productName());
            item.setSkuName(line.sku().specName());
            item.setProductImage(line.sku().image() == null ? line.sku().productImage() : line.sku().image());
            item.setUnitPrice(line.sku().price());
            item.setQuantity(line.quantity());
            item.setTotalAmount(line.amount());
            orderItemMapper.insert(item);
        }

        // ④ 状态留痕：创建时 from 为 NULL（REQ-703）
        writeStatusLog(order, null, OrderStatus.PENDING_PAYMENT, OrderStatusLog.OPERATOR_SYSTEM, null, "创建订单");

        // ⑤ 下单请求幂等：并发重复提交会撞 uk_request_id，让全局事务整体回滚，
        //    由 OrderService 捕获后返回首次订单号（REQ-701 约定 2）
        OrderRequest request = new OrderRequest();
        request.setRequestId(command.requestId());
        request.setUserId(command.userId());
        request.setOrderNo(orderNo);
        orderRequestMapper.insert(request);

        // ⑥ 15 分钟延时取消消息（同本地事务写入，提交后由投递任务按点发送，REQ-704）
        outboxService.append(properties.orderTimeoutTopic(), TAG_TIMEOUT, orderNo,
                new OrderTimeoutEvent(orderNo), order.getTimeoutAt());

        log.info("下单成功 orderNo={} userId={} items={} payAmount={}", orderNo, command.userId(),
                command.lines().size(), order.getPayAmount());
        return order;
    }

    private void writeStatusLog(Order order, OrderStatus from, OrderStatus to,
                                String operatorType, Long operatorId, String reason) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setOrderNo(order.getOrderNo());
        log.setFromStatus(from == null ? null : from.name());
        log.setToStatus(to.name());
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        statusLogMapper.insert(log);
    }
}
