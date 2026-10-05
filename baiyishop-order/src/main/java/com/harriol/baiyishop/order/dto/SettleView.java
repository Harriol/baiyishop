package com.harriol.baiyishop.order.dto;

import java.util.List;

/**
 * 结算试算结果（docs/api.md 4.7、REQ-602）。
 * <p>商品金额与应付金额都由服务端算；全场包邮，freightAmount 恒为 0。
 */
public record SettleView(
        List<ItemPreview> items,
        List<ItemPreview> invalidItems,
        Long totalAmount,
        Long freightAmount,
        Long payAmount,
        AddressSnapshot defaultAddress) {
}
