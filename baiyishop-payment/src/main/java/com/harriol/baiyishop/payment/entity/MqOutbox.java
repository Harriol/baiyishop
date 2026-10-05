package com.harriol.baiyishop.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 本地消息表（docs/database.md 9.2）：支付成功事件与支付单同事务写入，投递失败可重放。 */
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

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
