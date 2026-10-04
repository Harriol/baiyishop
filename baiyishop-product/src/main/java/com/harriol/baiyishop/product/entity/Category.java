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
 * 分类（docs/database.md 4.1，REQ-201）。
 * <p>最多 3 级，path 是物化路径（如 /1/12/135/），用于「按分类含子分类」查询，
 * 与 ES 的 categoryPath 字段一一对应（REQ-205）。
 */
@Getter
@Setter
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类，0 为顶级 */
    private Long parentId;

    private String name;

    /** 层级 1 / 2 / 3 */
    private Integer level;

    /** 物化路径，如 /1/12/135/ */
    private String path;

    private String icon;

    private Integer sort;

    /** 1 显示 / 0 隐藏（隐藏后前台不可见） */
    private Integer visible;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}