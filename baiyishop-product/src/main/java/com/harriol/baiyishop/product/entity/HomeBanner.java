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
 * 首页轮播（docs/database.md 4.9、REQ-401）。
 * <p>本表没有 deleted 字段，配置类数据走物理替换。
 */
@Getter
@Setter
@TableName("home_banner")
public class HomeBanner {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String imageUrl;

    /** 0 无跳转 / 1 商品 / 2 分类 / 3 外链 */
    private Integer linkType;

    private String linkValue;

    private Integer sort;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}