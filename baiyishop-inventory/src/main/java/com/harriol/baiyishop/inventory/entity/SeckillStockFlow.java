package com.harriol.baiyishop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 秒杀池流水（docs/database.md 5.4）。biz_key 唯一，保证划拨 / 回补 / 扣减的幂等。 */
@Getter
@Setter
@TableName("seckill_stock_flow")
public class SeckillStockFlow {

    public static final String TYPE_ALLOCATE = "ALLOCATE";
    public static final String TYPE_DEDUCT = "DEDUCT";
    public static final String TYPE_RETURN = "RETURN";
    public static final String TYPE_ROLLBACK = "ROLLBACK";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizKey;

    private Long poolId;

    private Long activitySkuId;

    private String type;

    private Integer quantity;

    private Integer beforeRemaining;

    private Integer afterRemaining;

    private LocalDateTime createdAt;
}