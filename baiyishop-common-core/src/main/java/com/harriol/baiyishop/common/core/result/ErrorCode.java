package com.harriol.baiyishop.common.core.result;

/**
 * 统一错误码。
 * <p>码段划分见 docs/api.md 第 3 章：1xxxx 通用、2xxxx 用户、3xxxx 商品、4xxxx 库存、5xxxx 订单、6xxxx 支付、7xxxx 秒杀。
 * 传输层用 HTTP 状态码，业务层用本枚举的 code。
 */
public enum ErrorCode {

    SUCCESS(0, "success", 200),

    PARAM_INVALID(10001, "参数错误", 400),
    UNAUTHORIZED(10002, "登录已失效，请重新登录", 401),
    FORBIDDEN(10003, "无权限执行该操作", 403),
    NOT_FOUND(10004, "请求的资源不存在", 404),
    TOO_MANY_REQUESTS(10005, "操作过于频繁，请稍后再试", 429),
    SYSTEM_ERROR(10006, "系统繁忙，请稍后重试", 500),

    // ---- 2xxxx 用户与账号 ----
    USER_CREDENTIALS_INVALID(20001, "账号或密码错误", 200),
    USER_ACCOUNT_LOCKED(20002, "账号已锁定，请 10 分钟后重试", 200),
    USER_ALREADY_EXISTS(20003, "该账号已被注册", 200),
    USER_DISABLED(20004, "账号已被停用", 200),
    USER_WECHAT_AUTH_FAILED(20005, "微信授权失败，请重试", 200),
    USER_ADDRESS_NOT_FOUND(20006, "收货地址不存在", 200),
    USER_ADDRESS_FORBIDDEN(20007, "收货地址不存在或不属于当前用户", 200),
    ADMIN_CREDENTIALS_INVALID(20008, "账号或密码错误", 200),
    ADMIN_DISABLED(20009, "账号已被停用", 200);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
