package com.harriol.baiyishop.product.dto.home;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 轮播项（读取与保存共用同一结构，后台整表替换）。 */
public record BannerItem(
        @Size(max = 100, message = "标题最长 100 个字符") String title,

        @NotBlank(message = "请上传轮播图片") String imageUrl,

        /** 0 无跳转 / 1 商品 / 2 分类 / 3 外链 */
        Integer linkType,

        String linkValue,

        Integer sort,

        Boolean enabled) {
}