package com.harriol.baiyishop.order.domain;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;

import java.util.Arrays;

/**
 * 订单状态机（REQ-703）。
 * <p>合法流转：待付款 → 待发货 → 待收货 → 已完成，且待付款可转已取消。
 * 其余组合一律拒绝（50002），避免出现「已取消又发货」这类脏状态。
 */
public enum OrderStatus {

    PENDING_PAYMENT("待付款"),
    PENDING_SHIPMENT("待发货"),
    PENDING_RECEIPT("待收货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 按码解析，未知码按参数错误处理（前端传错状态不应静默当作 ALL） */
    public static OrderStatus of(String code) {
        return Arrays.stream(values())
                .filter(status -> status.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "未知的订单状态：" + code));
    }

    /** 仅待付款可取消（REQ-705）；超时取消同样只对已支付的订单生效 */
    public boolean cancellable() {
        return this == PENDING_PAYMENT;
    }

    /** 仅待发货可发货（REQ-708） */
    public boolean shippable() {
        return this == PENDING_SHIPMENT;
    }

    /** 仅待收货可确认收货（REQ-706） */
    public boolean receivable() {
        return this == PENDING_RECEIPT;
    }
}
