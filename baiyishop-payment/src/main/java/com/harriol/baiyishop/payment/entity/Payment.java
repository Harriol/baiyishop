package com.harriol.baiyishop.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 支付单（docs/database.md 7.1，REQ-801 ~ REQ-803）。
 * <p>金额必须等于订单的 `pay_amount`（发起支付时校验，回调时再校验一次）；
 * 状态流转同样只用条件更新，回调重复投递天然幂等。
 */
@Getter
@Setter
@TableName("payment")
public class Payment {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private String orderNo;

    private Long userId;

    /** WECHAT / ALIPAY（本期为模拟实现，接口形状按真实渠道设计） */
    private String channel;

    /** 支付金额（分） */
    private Long amount;

    private String status;

    private String channelTradeNo;

    private LocalDateTime payTime;

    /** 与订单的 15 分钟支付超时对齐 */
    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
