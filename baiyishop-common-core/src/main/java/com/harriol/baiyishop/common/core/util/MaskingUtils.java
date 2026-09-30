package com.harriol.baiyishop.common.core.util;

/**
 * 脱敏工具。手机号等个人信息接口返回时必须脱敏（REQ-104、NFR-03）。
 */
public final class MaskingUtils {

    private MaskingUtils() {
    }

    /** 手机号脱敏：13800138888 → 138****8888 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 邮箱脱敏：abcdef@x.com → ab****@x.com */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        if (name.length() <= 2) {
            return "*" + email.substring(at - 1);
        }
        return name.substring(0, 2) + "****" + email.substring(at);
    }
}
