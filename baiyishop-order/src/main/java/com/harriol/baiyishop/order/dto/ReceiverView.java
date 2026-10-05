package com.harriol.baiyishop.order.dto;

/**
 * 收货信息（订单里的地址快照）。
 * <p>电话返回**完整号码**：这是下单人自己的订单，也是物流真正要用的信息；
 * 脱敏规则针对的是日志与公共响应（NFR-03）。
 */
public record ReceiverView(String name, String phone, String address) {
}
