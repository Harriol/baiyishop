package com.harriol.baiyishop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 本地消息表（docs/database.md 9.2、docs/adr/ADR-005）。
 * <p>与商品侧的区别：订单的两条消息都是延时消息，故多了 {@code deliverAt}
 * （见 V5 迁移说明）。投递失败仍按指数退避重试，超过上限置 FAILED 等人工重放。
 */
@Getter
@Setter
@TableName("mq_outbox")
public class MqOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String topic;

    private String tag;

    private String bizKey;

    private String payload;

    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    /** 计划投递时间；为空表示立即投递 */
    private LocalDateTime deliverAt;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
