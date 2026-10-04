package com.harriol.baiyishop.search.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时确保索引与别名存在（REQ-302 的「首次初始化」）。
 * <p>建索引失败**不让服务起不来**：ES 短暂不可用时搜索服务仍应注册到 Nacos，
 * 待 ES 恢复后由全量重建接口补齐（docs/architecture.md 15 章风险表）。
 */
@Component
public class SearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final SearchIndexService indexService;

    public SearchIndexInitializer(SearchIndexService indexService) {
        this.indexService = indexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            indexService.ensureIndex();
        } catch (Exception ex) {
            log.warn("初始化搜索索引失败，服务继续启动，可用 POST /internal/search/reindex 补齐：{}", ex.getMessage());
        }
    }
}
