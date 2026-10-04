package com.harriol.baiyishop.search.source;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.search.dto.IndexDocView;

import java.util.Optional;

/**
 * 索引文档来源（product-service 的内部接口）。
 * <p>抽出接口是为了让增量同步 / 全量重建的编排逻辑能脱离 HTTP 单独测试。
 */
public interface ProductIndexSource {

    /** 单个商品；商品不存在或已删除返回 empty（搜索侧据此移除文档） */
    Optional<IndexDocView> fetch(long productId);

    /** 分页全量拉取（含下架商品），用于全量重建 */
    PageResult<IndexDocView> fetchPage(long page, long size);
}
