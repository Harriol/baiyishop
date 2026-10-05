package com.harriol.baiyishop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 订单状态流转留痕（docs/database.md 6.4、REQ-703）。 */
@Getter
@Setter
@TableName("order_status_log")
public class OrderStatusLog {

    public static final String OPERATOR_SYSTEM = "SYSTEM";
    public static final String OPERATOR_USER = "USER";
    public static final String OPERATOR_ADMIN = "ADMIN";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    /** 创建订单时为 NULL */
    private String fromStatus;

    private String toStatus;

    private String operatorType;

    private Long operatorId;

    private String reason;

    private LocalDateTime createdAt;
}
