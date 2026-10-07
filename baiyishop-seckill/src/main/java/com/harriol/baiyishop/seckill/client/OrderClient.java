package com.harriol.baiyishop.seckill.client;

import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 订单域内部调用。
 * <p>两处用途：补偿任务按 ticketId 回查订单是否已落单（以 MySQL 为准修正 Redis，ADR-008）、
 * 以及把「已取消」的秒杀订单通知给订单侧（超时取消由订单侧发起，这里用于对账）。
 */
@Component
public class OrderClient {

    private static final String SERVICE = "http://baiyishop-order";

    private final InternalApiClient client;

    public OrderClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    /** 按票据查订单号；没有订单返回 empty */
    public String orderNoOfTicket(String ticketId) {
        // 订单服务的约定是「没有对应订单返回 50001」，必须把它声明为「允许为空」，
        // 否则对账任务会在这里抛异常，导致「预扣成功但没落单」的票据永远停在 QUEUED、库存不回收
        return client.getOptional("/internal/orders/by-ticket/" + ticketId, TicketOrderView.class,
                        ErrorCode.ORDER_NOT_FOUND.getCode())
                .map(TicketOrderView::orderNo)
                .orElse(null);
    }

    private record TicketOrderView(String orderNo) {
    }
}
