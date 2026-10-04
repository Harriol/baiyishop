package com.harriol.baiyishop.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新增 / 修改收货地址的请求（REQ-103）。 */
public record AddressRequest(
        @NotBlank(message = "请填写收货人") @Size(max = 50, message = "收货人最长 50 个字符")
        String receiverName,

        @NotBlank(message = "请填写手机号")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String receiverPhone,

        @NotBlank(message = "请选择省份") String province,
        @NotBlank(message = "请选择城市") String city,
        @NotBlank(message = "请选择区县") String district,

        @NotBlank(message = "请填写详细地址") @Size(max = 200, message = "详细地址最长 200 个字符")
        String detail,

        Boolean isDefault) {
}