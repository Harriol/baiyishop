package com.harriol.baiyishop.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import co.elastic.clients.elasticsearch.indices.update_aliases.AddAction;
import co.elastic.clients.elasticsearch.indices.update_aliases.RemoveAction;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.search.config.SearchProperties;
import com.harriol.baiyishop.search.dto.IndexDocView;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 索引生命周期与写入（REQ-301、REQ-302）。
 * <p>查询与增量写入都走**别名**：全量重建时先写临时物理索引，再一次性把别名切过去，
 * 重建期间搜索不中断（ADR-005）。
 * <p>增量写入带 refresh，把「索引同步 P95 ≤ 5s」从「依赖 ES 默认 1s 刷新 + 轮询」变成确定行为；
 * 单文档增量频率低，这个代价可接受。
 */
@Service
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);

    private final ElasticsearchClient client;
    private final RestClient restClient;
    private final SearchProperties properties;

    public SearchIndexService(ElasticsearchClient client, RestClient restClient, SearchProperties properties) {
        this.client = client;
        this.restClient = restClient;
        this.properties = properties;
    }

    public String alias() {
        return properties.indexAlias();
    }

    /** 首次启动时按需建索引；已存在则什么都不做 */
    public void ensureIndex() {
        if (aliasExists()) {
            return;
        }
        createIndex(alias() + "_v1", alias());
        log.info("已创建搜索索引 {}_{} 并绑定别名 {}", alias(), "v1", alias());
    }

    public boolean aliasExists() {
        return call(() -> client.indices().existsAlias(e -> e.name(alias())).value());
    }

    /** 创建物理索引；alias 为 null 时不绑定别名（全量重建的临时索引） */
    public void createIndex(String physicalIndex, String alias) {
        Request request = new Request("PUT", "/" + physicalIndex);
        request.setJsonEntity(ProductIndexDefinition.json(alias));
        try {
            restClient.performRequest(request);
        } catch (ResponseException ex) {
            if (ex.getResponse().getStatusLine().getStatusCode() == 400) {
                // 并发启动时可能被另一个实例抢先建好，视为成功
                log.info("索引 {} 已存在，跳过创建", physicalIndex);
                return;
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR, "创建搜索索引失败：" + ex.getMessage());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "搜索索引暂时不可用，请稍后重试");
        }
    }

    /** 单条文档写入（增量同步） */
    public void upsert(IndexDocView doc) {
        EsProductDoc esDoc = EsProductDoc.from(doc);
        call(() -> client.index(i -> i.index(alias()).id(String.valueOf(esDoc.id())).document(esDoc)
                .refresh(Refresh.True)));
        log.debug("索引文档已更新 id={} status={}", esDoc.id(), esDoc.status());
    }

    /** 删除文档（商品被删除，或回查发现已不存在） */
    public void delete(long productId) {
        call(() -> client.delete(d -> d.index(alias()).id(String.valueOf(productId)).refresh(Refresh.True)));
    }

    /** 批量写入（全量重建），返回写入条数 */
    public int bulkIndex(String physicalIndex, List<IndexDocView> docs) {
        if (docs.isEmpty()) {
            return 0;
        }
        List<BulkOperation> operations = docs.stream()
                .map(EsProductDoc::from)
                .map(doc -> BulkOperation.of(b -> b.index(i -> i
                        .id(String.valueOf(doc.id()))
                        .document(doc))))
                .toList();
        BulkResponse response = call(() -> client.bulk(b -> b.index(physicalIndex).operations(operations)));
        if (response.errors()) {
            response.items().stream()
                    .filter(item -> item.error() != null)
                    .limit(5)
                    .forEach(item -> log.error("批量索引失败 id={} reason={}", item.id(),
                            item.error() == null ? null : item.error().reason()));
            throw new BizException(ErrorCode.SYSTEM_ERROR, "全量重建存在写入失败，请查看日志后重试");
        }
        return docs.size();
    }

    public void refresh(String index) {
        call(() -> client.indices().refresh(r -> r.index(index)));
    }

    /** 别名当前指向的物理索引；别名不存在时返回空列表 */
    public List<String> indicesOfAlias() {
        try {
            return new ArrayList<>(call(() -> client.indices().getAlias(g -> g.name(alias()))).result().keySet());
        } catch (ElasticsearchException ex) {
            if (ex.response() != null && ex.response().status() == 404) {
                return List.of();
            }
            throw ex;
        }
    }

    /** 原子切换别名：一次 _aliases 调用里同时 add 新索引 / remove 旧索引 */
    public void swapAlias(String newIndex) {
        List<Action> actions = new ArrayList<>();
        actions.add(Action.of(a -> a.add(AddAction.of(x -> x.index(newIndex).alias(alias())))));
        for (String old : indicesOfAlias()) {
            if (!old.equals(newIndex)) {
                actions.add(Action.of(a -> a.remove(RemoveAction.of(r -> r.index(old).alias(alias())))));
            }
        }
        call(() -> client.indices().updateAliases(u -> u.actions(actions)));
        log.info("搜索索引别名 {} 已切换到 {}", alias(), newIndex);
    }

    public void deleteIndex(String index) {
        try {
            call(() -> client.indices().delete(d -> d.index(index)));
            log.info("已删除旧索引 {}", index);
        } catch (BizException ex) {
            // 清理旧索引失败不影响搜索可用性，记日志即可
            log.warn("删除旧索引 {} 失败：{}", index, ex.getMessage());
        }
    }

    private <T> T call(IoSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "搜索索引暂时不可用，请稍后重试");
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
