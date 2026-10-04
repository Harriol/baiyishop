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
 * 楼层手动选品（docs/database.md 4.13）。
 * <p>**可选**：为空时楼层完全按「分类 + 排序维度」自动拉取；写入后这些商品在楼层内置顶。
 */
@Getter
@Setter
@TableName("home_floor_item")
public class HomeFloorItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long floorId;

    private Long productId;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}