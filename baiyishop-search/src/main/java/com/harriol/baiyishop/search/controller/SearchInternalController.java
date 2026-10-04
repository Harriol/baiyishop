package com.harriol.baiyishop.search.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.search.dto.ReindexResult;
import com.harriol.baiyishop.search.index.SearchReindexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索内部接口（docs/api.md 第 6 章）。
 * <p>网关对外拦截 /internal/**（返回 404），因此这里不做令牌校验。
 * 全量重建是运维动作（首次初始化、索引损坏恢复、每日对账发现落后时手动补齐）。
 */
@RestController
@RequestMapping("/internal/search")
public class SearchInternalController {

    private final SearchReindexService reindexService;

    public SearchInternalController(SearchReindexService reindexService) {
        this.reindexService = reindexService;
    }

    /** 全量重建索引：重建期间查询走旧别名，切完别名才删除旧索引（REQ-302） */
    @PostMapping("/reindex")
    public Result<ReindexResult> reindex(@RequestParam(defaultValue = "200") int size) {
        return Result.ok(reindexService.reindex(size));
    }
}
