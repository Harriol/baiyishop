package com.harriol.baiyishop.product.dto.home;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 公告项。 */
public record NoticeItem(
        @NotBlank(message = "请填写公告内容") @Size(max = 500, message = "公告最长 500 个字符") String content,

        Integer sort,

        Boolean enabled) {
}