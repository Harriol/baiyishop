package com.harriol.baiyishop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 库存预警（docs/database.md 5.5、REQ-505）。
 * <p>库存变更后若 available <= warn_threshold 生成 / 复用一条 OPEN 预警；补货后自动关闭。
 */
@Getter
@Setter
@TableName("stock_alert")
public class StockAlert {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private Long productId;

    /** 触发时库存 */
    private Integer currentStock;

    /** 触发时阈值 */
    private Integer threshold;

    private String status;

    private LocalDateTime handledAt;

    private LocalDateTime updatedAt;
}