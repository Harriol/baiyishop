package com.harriol.baiyishop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 库存权威表（docs/database.md 5.1）。
 * <p>不变量：available >= 0、locked >= 0，且总库存 = available + locked。
 * 所有变更都走条件更新（WHERE available >= n），并写 inventory_flow 留痕。
 * <p>本表是记录型表，按设计**没有 created_at**，只有 updated_at。
 */
@Getter
@Setter
@TableName("inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private Long productId;

    /** 可售库存（下单可占用量） */
    private Integer available;

    /** 锁定库存（已下单待付款占用） */
    private Integer locked;

    /** 预警阈值 */
    private Integer warnThreshold;

    /** 乐观锁版本 */
    private Integer version;

    private LocalDateTime updatedAt;
}