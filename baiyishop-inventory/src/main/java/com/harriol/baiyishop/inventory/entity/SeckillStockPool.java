package com.harriol.baiyishop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 秒杀库存池（docs/database.md 5.3，REQ-504）。
 * <p>归 inventory-service 所有：库存变动集中在一处，才能保证「不超卖、不为负、可追溯」（ADR-004）。
 * <p>不变量：remaining >= 0，且 remaining + sold = total。
 */
@Getter
@Setter
@TableName("seckill_stock_pool")
public class SeckillStockPool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long activitySkuId;

    private Long skuId;

    /** 划拨总量（划拨后不变） */
    private Integer total;

    /** 秒杀池剩余 */
    private Integer remaining;

    private Integer sold;

    private Integer version;

    private LocalDateTime updatedAt;
}