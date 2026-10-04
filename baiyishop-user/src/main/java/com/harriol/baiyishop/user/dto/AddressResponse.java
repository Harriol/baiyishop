package com.harriol.baiyishop.user.dto;

import com.harriol.baiyishop.common.core.util.MaskingUtils;
import com.harriol.baiyishop.user.entity.UserAddress;

/** 收货地址响应（手机号脱敏）。 */
public record AddressResponse(Long id, String receiverName, String receiverPhone,
                              String province, String city, String district, String detail,
                              boolean isDefault) {

    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getReceiverName(),
                MaskingUtils.maskPhone(address.getReceiverPhone()),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetail(),
                address.getIsDefault() != null && address.getIsDefault() == 1);
    }
}