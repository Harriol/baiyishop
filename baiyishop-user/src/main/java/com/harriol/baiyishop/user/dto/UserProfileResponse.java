package com.harriol.baiyishop.user.dto;

import com.harriol.baiyishop.common.core.util.MaskingUtils;
import com.harriol.baiyishop.user.entity.User;

/** 用户资料（手机号已脱敏，REQ-104）。 */
public record UserProfileResponse(Long id, String username, String nickname, String avatar, String phone) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                MaskingUtils.maskPhone(user.getPhone()));
    }
}
