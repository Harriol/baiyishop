package com.harriol.baiyishop.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 活动商品（docs/database.md 8.2，REQ-901）。
 * <p>alloc_stock 是「划拨意图」，权威剩余量在 inventory 的 seckill_stock_pool（ADR-004）。
 */
@Getter
@Setter
@TableName("seckill_activity_sku")
public class SeckillActivitySku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long skuId;

    private Long productId;

    /** 秒杀价（分） */
    private Long seckillPrice;

    /** 原价（分，展示划线价） */
    private Long originalPrice;

    private Integer allocStock;

    /** 每人限购数 */
    private Integer limitPerUser;

    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
