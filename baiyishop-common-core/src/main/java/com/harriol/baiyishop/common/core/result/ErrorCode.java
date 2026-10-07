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
    FILE_TYPE_NOT_ALLOWED(10007, "只支持 JPG / PNG / WebP / GIF 图片", 400),
    FILE_TOO_LARGE(10008, "图片大小不能超过 5MB", 400),
    FILE_UPLOAD_FAILED(10009, "文件上传失败，请稍后重试", 500),

    // ---- 2xxxx 用户与账号 ----
    USER_CREDENTIALS_INVALID(20001, "账号或密码错误", 200),
    USER_ACCOUNT_LOCKED(20002, "账号已锁定，请 10 分钟后重试", 200),
    USER_ALREADY_EXISTS(20003, "该账号已被注册", 200),
    USER_DISABLED(20004, "账号已被停用", 200),
    USER_WECHAT_AUTH_FAILED(20005, "微信授权失败，请重试", 200),
    USER_ADDRESS_NOT_FOUND(20006, "收货地址不存在", 200),
    USER_ADDRESS_FORBIDDEN(20007, "收货地址不存在或不属于当前用户", 200),
    ADMIN_CREDENTIALS_INVALID(20008, "账号或密码错误", 200),
    ADMIN_DISABLED(20009, "账号已被停用", 200),

    // ---- 3xxxx 商品与分类 ----
    CATEGORY_NOT_FOUND(30001, "分类不存在", 200),
    CATEGORY_HAS_CHILDREN(30002, "该分类下存在子分类，无法删除", 200),
    CATEGORY_IN_USE(30003, "该分类已被商品引用，无法删除", 200),
    CATEGORY_LEVEL_EXCEEDED(30004, "分类最多支持 3 级", 200),
    BRAND_NOT_FOUND(30005, "品牌不存在", 200),
    BRAND_IN_USE(30006, "该品牌已被商品引用，无法删除", 200),
    PRODUCT_NOT_FOUND(30007, "商品不存在", 200),
    PRODUCT_OFF_SALE(30008, "商品已下架", 200),
    PARAM_ITEM_TEMPLATE_MISMATCH(30009, "参数项与所选模板不匹配", 200),
    PARAM_TEMPLATE_NOT_FOUND(30010, "参数模板不存在", 200),
    PARAM_ITEM_NOT_FOUND(30011, "参数项不存在", 200),
    PARAM_ITEM_IN_USE(30012, "该参数项已被商品使用，无法删除", 200),
    PARAM_TEMPLATE_IN_USE(30013, "该模板下存在参数项，无法删除", 200),

    // ---- 4xxxx 库存 ----
    INSUFFICIENT_STOCK(40001, "库存不足", 200),
    INVENTORY_NOT_FOUND(40002, "库存记录不存在", 200),
    INVALID_STOCK_ADJUSTMENT(40003, "库存调整数量非法", 200),
    SECKILL_STOCK_POOL_NOT_FOUND(40004, "秒杀库存池不存在", 200),
    ALLOCATION_EXCEEDS_AVAILABLE(40005, "划拨数量超过当前可售库存", 200),

    // ---- 5xxxx 购物车与订单 ----
    ORDER_NOT_FOUND(50001, "订单不存在", 200),
    ORDER_STATUS_NOT_ALLOWED(50002, "当前订单状态不支持该操作", 200),
    CART_ITEM_INVALID(50003, "部分商品已失效，请重新选择", 200),
    CART_EMPTY_CHECKED(50004, "请选择要结算的商品", 200),
    ORDER_ADDRESS_REQUIRED(50005, "请选择收货地址", 200),
    CART_QUANTITY_EXCEEDED(50006, "超出单品限购数量", 200),
    CART_ITEM_NOT_FOUND(50007, "购物车条目不存在", 200),

    // ---- 6xxxx 支付 ----
    PAYMENT_NOT_FOUND(60001, "支付单不存在", 200),
    PAYMENT_NOT_ALLOWED(60002, "该订单当前不可支付", 200),
    PAYMENT_AMOUNT_MISMATCH(60003, "支付金额与订单不一致", 200),
    PAYMENT_SIGN_INVALID(60004, "回调验签失败", 200),

    // ---- 7xxxx 秒杀 ----
    SECKILL_ACTIVITY_NOT_FOUND(70001, "秒杀活动不存在", 200),
    SECKILL_NOT_STARTED(70002, "秒杀尚未开始", 200),
    SECKILL_ENDED(70003, "秒杀已结束", 200),
    SECKILL_SOLD_OUT(70004, "秒杀商品已售罄", 200),
    SECKILL_LIMIT_EXCEEDED(70005, "超出限购数量", 200),
    SECKILL_SKU_NOT_ON_SALE(70006, "秒杀商品未上架", 200),
    SECKILL_RECORD_NOT_FOUND(70007, "抢购记录不存在", 200);

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

    /**
     * 按错误码反查枚举，用于**原样透传**内部服务返回的业务码（如库存 40001、商品 30007）。
     * <p>未知码归入系统错误，避免把上游的陌生码直接抛给前端。
     */
    public static ErrorCode of(int code) {
        for (ErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return SYSTEM_ERROR;
    }
}
