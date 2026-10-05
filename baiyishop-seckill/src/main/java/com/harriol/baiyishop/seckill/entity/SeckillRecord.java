package com.harriol.baiyishop.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 抢购记录与结果（docs/database.md 8.3，REQ-903、REQ-904）。
 * <p>预扣成功后先落一条 QUEUED（同事务写 mq_outbox），异步下单完成后再更新为 SUCCESS / FAILED。
 * <p>uk_user_request (activity_sku_id, user_id, request_id) 是请求级幂等的落点：
 * 同一用户带同一 requestId 重复请求返回同一张票据（REQ-903）。
 */
@Getter
@Setter
@TableName("seckill_record")
public class SeckillRecord {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public static final String FAIL_SOLD_OUT = "SOLD_OUT";
    public static final String FAIL_LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    public static final String FAIL_NOT_STARTED = "ACTIVITY_NOT_STARTED";
    public static final String FAIL_ENDED = "ACTIVITY_ENDED";
    public static final String FAIL_SYSTEM_ERROR = "SYSTEM_ERROR";
    public static final String FAIL_NO_ADDRESS = "NO_ADDRESS";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketId;

    private Long activityId;

    private Long activitySkuId;

    private Long userId;

    private String requestId;

    private Integer quantity;

    /** QUEUED / SUCCESS / FAILED */
    private String status;

    private String failReason;

    private String orderNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
