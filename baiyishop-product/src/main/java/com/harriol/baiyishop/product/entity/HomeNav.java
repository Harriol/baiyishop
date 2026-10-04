package com.harriol.baiyishop.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 首页金刚区（分类快捷入口，docs/database.md 4.11、REQ-401）。 */
@Getter
@Setter
@TableName("home_nav")
public class HomeNav {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String icon;

    private Long categoryId;

    private Integer sort;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}