package com.harriol.baiyishop.order.dto;

/**
 * user-service 的收货地址快照视图（docs/api.md 第 6 章）。
 * <p>与展示用的地址不同，这里带**完整手机号**：订单快照要能真的联系到收货人，
 * 脱敏只是前台展示层的事。
 */
public record AddressSnapshot(
        Long id,
        Long userId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detail) {
}
