package com.harriol.baiyishop.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品参数值（docs/database.md 4.8，REQ-204）。
 * <p>本表**不冗余参数项名称**：参数项由后台统一维护，改名后所有商品详情应同步生效，
 * 因此展示时按 param_item_id 取名。
 * <p>本表没有 deleted 字段，是物理删除。
 */
@Getter
@Setter
@TableName("product_param_value")
public class ProductParamValue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Long paramItemId;

    private String value;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}