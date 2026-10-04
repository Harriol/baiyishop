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
 * <p>不变量：remaining >= 0 且 remaining + sold <= total。
 * <p>差额（total - remaining - sold）是**活动结束已回补**的数量：total 表示划拨总量这个
 * 不可变的历史事实，未售出部分回补普通库存后 remaining 归零，因此三者不再相等。
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