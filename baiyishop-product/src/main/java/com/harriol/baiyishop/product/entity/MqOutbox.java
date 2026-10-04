package com.harriol.baiyishop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 本地消息表（docs/database.md 9.2、docs/adr/ADR-005）。
 * <p>商品写库与写本表在**同一个本地事务**内完成，再由投递任务异步发到 RocketMQ。
 * 这样即使消息投递失败，业务数据也已经落库，消息可以重投，「业务提交了但消息没发出去」不会发生。
 * <p>本表是 APPEND-ONLY 的追加表，只新增与更新状态，不做逻辑删除。
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

    /** 事件 ID，消费侧幂等键 */
    private String eventId;

    private String topic;

    /** 标签（事件类型）：UPSERT / DELETE */
    private String tag;

    /** 业务键（这里为 productId） */
    private String bizKey;

    private String payload;

    /** PENDING / SENT / FAILED */
    private String status;

    private Integer retryCount;

    /** 下次重试时间（退避），NULL 表示立即可投递 */
    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
