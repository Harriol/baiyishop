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
 * 首页楼层（docs/database.md 4.12、REQ-402）。
 * <p>楼层商品按绑定分类自动拉取，**每个楼层可单独配置排序维度**（R5-Q6）。
 * <p>sort_field 用 TINYINT 存：1 销量 / 2 上新 / 3 价格升 / 4 价格降；
 * 对外接口统一用字符串（sales/new/price_asc/price_desc），与本项目其他接口保持一致。
 */
@Getter
@Setter
@TableName("home_floor")
public class HomeFloor {

    public static final int SORT_SALES = 1;
    public static final int SORT_NEW = 2;
    public static final int SORT_PRICE_ASC = 3;
    public static final int SORT_PRICE_DESC = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 绑定分类，前台按此分类（含子分类）自动拉取商品 */
    private Long categoryId;

    private Integer sortField;

    /** 展示数量 */
    private Integer limitSize;

    private Integer sort;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}