package com.harriol.baiyishop.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料（REQ-104）。
 * <p>字段为 null 表示不修改；昵称与手机号都为空时视为无效请求。
 *
 * @param nickname 昵称，最长 50 个字符
 * @param avatar   头像地址（可选，图片上传由后台接口提供）
 * @param phone    手机号，11 位大陆手机号；传空串表示解绑
 */
public record UpdateProfileRequest(
        @Size(max = 50, message = "昵称最长 50 个字符") String nickname,

        @Size(max = 500, message = "头像地址最长 500 个字符") String avatar,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone) {
}
