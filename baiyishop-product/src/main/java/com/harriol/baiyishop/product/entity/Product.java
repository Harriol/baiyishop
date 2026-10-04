package com.harriol.baiyishop.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品 SPU（docs/database.md 4.3，REQ-203、REQ-206）。
 * <p>min_price 与 sales 是展示用冗余，权威值分别在 product_sku.price 与订单完成统计。
 * <p>本表与 product_sku 都不存库存字段，库存权威在 inventory-service（ADR-004、ADR-007）。
 */
@Getter
@Setter
@TableName("product")
public class Product {

    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_OFF_SALE = "OFF_SALE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long categoryId;

    private Long brandId;

    /** 主图（列表封面） */
    private String mainImage;

    /** 富文本详情 */
    private String detail;

    /** ON_SALE 上架 / OFF_SALE 下架 */
    private String status;

    /** 冗余：默认 SKU 价格（分） */
    private Long minPrice;

    /** 冗余：累计销量 */
    private Integer sales;

    private LocalDateTime onSaleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}