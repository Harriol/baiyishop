package com.harriol.baiyishop.search;

import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 清理测试索引。
 * <p>ES 默认 {@code action.destructive_requires_name=true}，不允许用通配符删除索引，
 * 因此先按通配符**列出**再逐个删（读操作允许通配符）。
 * <p>清理失败不抛异常：它是收尾动作，不该影响用例结论。
 */
final class TestIndexCleanup {

    private TestIndexCleanup() {
    }

    static void deleteAll(RestClient restClient, ObjectMapper objectMapper, String pattern) {
        try {
            Response response = restClient.performRequest(
                    new Request("GET", "/_cat/indices/" + pattern + "?h=index&format=json"));
            JsonNode indices = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
            for (JsonNode index : indices) {
                try {
                    restClient.performRequest(new Request("DELETE", "/" + index.get("index").asString()));
                } catch (Exception ignored) {
                    // 单个索引删除失败继续清理其它索引
                }
            }
        } catch (Exception ignored) {
            // 列表或解析失败时忽略：测试数据已用随机索引名隔离，不会污染真实索引
        }
    }
}
