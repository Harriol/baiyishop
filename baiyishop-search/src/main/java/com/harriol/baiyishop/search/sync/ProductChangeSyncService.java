package com.harriol.baiyishop.search.sync;

import com.harriol.baiyishop.search.dto.ProductChangedEventView;
import com.harriol.baiyishop.search.index.SearchIndexService;
import com.harriol.baiyishop.search.source.ProductIndexSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 商品变更 → 索引重建（REQ-302、docs/architecture.md 5.5）。
 * <p>三点设计：
 * <ol>
 *   <li>回查而非带内容：事件只给 productId，文档内容在这里回查最新值，
 *       于是消息乱序 / 重复投递都不会把旧数据写进索引</li>
 *   <li>删除即「查不到就删」：商品被逻辑删除后回查返回 30007，索引文档一并移除，
 *       不需要额外的事件类型来区分</li>
 *   <li>幂等标记在成功之后：先标记后处理的话，一旦索引写入失败，
 *       RocketMQ 重投会被去重挡掉，那条更新就永远丢了</li>
 * </ol>
 * <p>抛异常 = 通知 RocketMQ 重投（最多 3 次后进死信，由每日对账 + 全量重建兜底）。
 */
@Service
public class ProductChangeSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProductChangeSyncService.class);

    private final ObjectMapper objectMapper;
    private final ProductIndexSource source;
    private final SearchIndexService indexService;
    private final ConsumeDedupService dedup;

    public ProductChangeSyncService(ObjectMapper objectMapper,
                                    ProductIndexSource source,
                                    SearchIndexService indexService,
                                    ConsumeDedupService dedup) {
        this.objectMapper = objectMapper;
        this.source = source;
        this.indexService = indexService;
        this.dedup = dedup;
    }

    public void handle(String message) {
        ProductChangedEventView event = read(message);
        if (event == null || event.productId() == null) {
            log.warn("忽略无法解析的商品变更消息：{}", message);
            return;
        }
        if (dedup.seen(event.eventId())) {
            log.debug("商品变更事件已消费 eventId={}", event.eventId());
            return;
        }

        if (event.isDelete()) {
            indexService.delete(event.productId());
        } else {
            source.fetch(event.productId()).ifPresentOrElse(indexService::upsert, () -> {
                log.info("商品已不存在，移除索引文档 id={}", event.productId());
                indexService.delete(event.productId());
            });
        }
        dedup.mark(event.eventId());
    }

    /** 消息体由商品侧约定；解析失败按「无法处理」处理，不阻塞后续消息 */
    private ProductChangedEventView read(String message) {
        try {
            return objectMapper.readValue(message, ProductChangedEventView.class);
        } catch (Exception ex) {
            log.warn("商品变更消息解析失败：{}", message, ex);
            return null;
        }
    }
}
