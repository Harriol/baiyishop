package com.harriol.baiyishop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 库存流水（docs/database.md 5.2）。
 * <p>biz_key 是**幂等落点**：唯一索引，重复的锁定 / 扣减 / 释放请求会因唯一冲突被拒绝，
 * 服务捕获后按「已处理」返回成功语义（架构 6.2）。
 */
@Getter
@Setter
@TableName("inventory_flow")
public class InventoryFlow {

    public static final String TYPE_LOCK = "LOCK";
    public static final String TYPE_DEDUCT = "DEDUCT";
    public static final String TYPE_UNLOCK = "UNLOCK";
    public static final String TYPE_ADJUST = "ADJUST";
    public static final String TYPE_ALLOCATE = "ALLOCATE";
    public static final String TYPE_RETURN = "RETURN";
    public static final String TYPE_ROLLBACK = "ROLLBACK";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键：{orderNo}:{action} 或 ADJUST:{id} */
    private String bizKey;

    private Long skuId;

    private String type;

    private Integer quantity;

    private Integer beforeAvailable;

    private Integer afterAvailable;

    private Integer beforeLocked;

    private Integer afterLocked;

    private String reason;

    /** SYSTEM / ADMIN */
    private String operatorType;

    private Long operatorId;

    private LocalDateTime createdAt;
}