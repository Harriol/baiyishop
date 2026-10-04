package com.harriol.baiyishop.search.index;

/**
 * 索引结构定义（REQ-301）。
 * <p>中文分词用官方 smartcn 插件（ik 已不再发布安装包，见 deploy/elasticsearch/Dockerfile）：
 * 字段直接引用内置的 {@code smartcn} 分析器，因此 settings 里不必再自定义 analyzer。
 * <p>单分片 0 副本：单机 Docker 环境，副本只会在节点离开时变成 unassigned 拖垮集群健康。
 */
public final class ProductIndexDefinition {

    private ProductIndexDefinition() {
    }

    /**
     * 索引定义 JSON。
     *
     * @param alias 需要绑定的别名；全量重建的临时索引传 null（不带别名，切换时再加）
     */
    public static String json(String alias) {
        String aliases = alias == null ? "" : "\"aliases\": {\"" + alias + "\": {}},\n";
        return """
                {
                %s"settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0
                  },
                  "mappings": {
                    "properties": {
                      "id": { "type": "long" },
                      "name": {
                        "type": "text",
                        "analyzer": "smartcn",
                        "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } }
                      },
                      "mainImage": { "type": "keyword", "index": false },
                      "price": { "type": "long" },
                      "sales": { "type": "integer" },
                      "categoryId": { "type": "long" },
                      "categoryIds": { "type": "long" },
                      "categoryPath": { "type": "keyword" },
                      "categoryName": { "type": "keyword" },
                      "brandId": { "type": "long" },
                      "brandName": { "type": "keyword" },
                      "status": { "type": "keyword" },
                      "onSaleTime": { "type": "date", "format": "epoch_millis" }
                    }
                  }
                }
                """.formatted(aliases);
    }
}
