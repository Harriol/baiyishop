package com.harriol.baiyishop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 订单主表（docs/database.md 6.2，REQ-701 ~ REQ-708）。
 * <p>三个约定：
 * <ul>
 *   <li>order 是 MySQL 关键字，表名必须加反引号</li>
 *   <li>状态流转一律用**条件更新**（WHERE status = 期望值），影响行数为 0 即非法流转或已被处理，
 *       天然幂等，取消与超时并发时只有一方能成功（REQ-703、REQ-705）</li>
 *   <li>金额只存服务端计算结果（快照单价 × 数量），不存前端传值</li>
 * </ul>
 */
@Getter
@Setter
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    /** PENDING_PAYMENT / PENDING_SHIPMENT / PENDING_RECEIPT / COMPLETED / CANCELLED */
    private String status;

    /** CART / BUY_NOW / SECKILL */
    private String source;

    /** 秒杀票据号（仅秒杀订单有值，uk_seckill_ticket 保证一张票据只落一笔订单） */
    private String seckillTicketId;

    /** 商品金额合计（分） */
    private Long totalAmount;

    /** 运费（分）：全场包邮恒为 0 */
    private Long freightAmount;

    private Long payAmount;

    /** WECHAT / ALIPAY，支付成功后写入 */
    private String payType;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    private String cancelReason;

    /** 支付超时时间 = 创建时间 + 15 分钟（REQ-704） */
    private LocalDateTime timeoutAt;

    private LocalDateTime payTime;

    private LocalDateTime shipTime;

    private String trackingNo;

    /** 自动确认收货时间 = 发货时间 + 7 天（REQ-707） */
    private LocalDateTime autoReceiveAt;

    private LocalDateTime receiveTime;

    private LocalDateTime finishTime;

    private LocalDateTime cancelTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
