package com.harriol.baiyishop.payment.client;

import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.payment.dto.PayableOrderView;
import org.springframework.stereotype.Component;

/**
 * 订单域内部调用（docs/api.md 第 6 章）。
 * <p>发起支付前必须回查订单：状态是否待付款、金额是否一致、订单是否属于当前用户。只读，事务外调用。
 */
@Component
public class OrderClient {

    private static final String SERVICE = "http://baiyishop-order";

    private final InternalApiClient client;

    public OrderClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    /** 订单不存在时 order 侧返回 50001，客户端原样上抛 */
    public PayableOrderView payable(String orderNo) {
        return client.get("/internal/orders/" + orderNo + "/payable", PayableOrderView.class);
    }
}
