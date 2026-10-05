package com.harriol.baiyishop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 下单请求幂等表（docs/database.md 6.6、REQ-701）。
 * <p>{@code uk_request_id} 是幂等落点：同一 requestId 重复提交命中唯一冲突，
 * 服务返回**首次**的订单号，不产生第二笔订单。
 */
@Getter
@Setter
@TableName("order_request")
public class OrderRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private Long userId;

    private String orderNo;

    private LocalDateTime createdAt;
}
