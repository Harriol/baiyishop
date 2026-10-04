package com.harriol.baiyishop.search.dto;

/**
 * 全量重建结果（REQ-302）。
 *
 * @param index   本次重建写入的物理索引名
 * @param indexed 写入文档数
 * @param tookMillis 耗时
 */
public record ReindexResult(String index, long indexed, long tookMillis) {
}
