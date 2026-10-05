package com.harriol.baiyishop.order.domain;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;

/** 下单来源（docs/database.md 6.2）：购物车结算 / 立即购买（秒杀由 seckill 链路写入）。 */
public enum OrderSource {

    CART,
    BUY_NOW,
    SECKILL;

    public static OrderSource of(String code) {
        for (OrderSource source : values()) {
            if (source.name().equalsIgnoreCase(code)) {
                return source;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的下单来源：" + code);
    }
}
