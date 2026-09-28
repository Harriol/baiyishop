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
    SYSTEM_ERROR(10006, "系统繁忙，请稍后重试", 500);

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
