package com.harriol.baiyishop.product.dto.home;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 楼层配置项（REQ-402）。
 *
 * @param sortField         该楼层**独立**的排序维度：sales / new / price_asc / price_desc（R5-Q6）
 * @param limitSize         展示数量，1 ~ 50
 * @param pinnedProductIds  可选：人工置顶的商品，留空则完全按「分类 + 排序维度」自动拉取
 */
public record FloorItem(
        @NotBlank(message = "请填写楼层标题") @Size(max = 50, message = "标题最长 50 个字符") String title,

        @NotNull(message = "请选择楼层绑定的分类") Long categoryId,

        String sortField,

        @Min(value = 1, message = "展示数量至少 1") @Max(value = 50, message = "展示数量最多 50") Integer limitSize,

        Integer sort,

        Boolean enabled,

        List<Long> pinnedProductIds) {
}