package com.harriol.baiyishop.order.dto;

import com.harriol.baiyishop.order.domain.OrderSource;

import java.util.List;

/**
 * 下单命令：购物车结算与立即购买归一后的产物（docs/architecture.md 5.1 要点 7）。
 * <p>商品 / 地址读取已在事务外完成，进入全局事务的只有「锁库存 + 写订单」。
 */
public record OrderCommand(
        long userId,
        String requestId,
        OrderSource source,
        List<OrderLine> lines,
        AddressSnapshot address,
        String remark) {

    public String receiverAddress() {
        return address.province() + address.city() + address.district() + address.detail();
    }
}
