package com.harriol.baiyishop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 后台订单备注（docs/database.md 6.5）：客服角色唯一的写权限入口。 */
@Getter
@Setter
@TableName("order_note")
public class OrderNote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long adminId;

    /** 备注人姓名快照 */
    private String adminName;

    private String content;

    private LocalDateTime createdAt;
}
