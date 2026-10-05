package com.harriol.baiyishop.user.dto;

import com.harriol.baiyishop.user.entity.UserAddress;

/**
 * 收货地址快照（内部接口，供 order-service 下单使用）。
 * <p>与前台展示的地址不同：手机号**不脱敏** —— 订单快照要能真的联系到收货人，
 * 脱敏只属于展示层（NFR-03 约束的是日志与前台响应，不是订单存证）。
 */
public record AddressSnapshot(Long id, Long userId, String receiverName, String receiverPhone,
                              String province, String city, String district, String detail) {

    public static AddressSnapshot from(UserAddress address) {
        return new AddressSnapshot(address.getId(), address.getUserId(), address.getReceiverName(),
                address.getReceiverPhone(), address.getProvince(), address.getCity(),
                address.getDistrict(), address.getDetail());
    }
}
