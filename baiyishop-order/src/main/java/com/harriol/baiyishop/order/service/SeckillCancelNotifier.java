package com.harriol.baiyishop.order.service;

import com.harriol.baiyishop.order.config.OrderProperties;
import com.harriol.baiyishop.order.dto.OrderCancelEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀订单取消的通知（REQ-905）。
 * <p>秒杀订单的库存不在普通可售里，取消时**不能**走常规释放 —— 那会把秒杀库存还进普通库存。
 * 正确做法是把「已取消」写进本地消息表，由 seckill-service 消费后回补秒杀池并恢复限购计数；
 * 写消息与订单状态变更同事务，保证「订单取消了但库存没还」不会发生。
 */
@Service
public class SeckillCancelNotifier {

    private static final String TAG_CANCEL = "SECKILL_CANCEL";

    private final OrderOutboxService outboxService;
    private final OrderProperties properties;

    public SeckillCancelNotifier(OrderOutboxService outboxService, OrderProperties properties) {
        this.outboxService = outboxService;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String orderNo) {
        outboxService.append(properties.seckillCancelTopic(), TAG_CANCEL, orderNo,
                new OrderCancelEvent(orderNo), null);
    }
}
