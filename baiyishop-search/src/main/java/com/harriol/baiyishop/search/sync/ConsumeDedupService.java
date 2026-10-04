package com.harriol.baiyishop.search.sync;

import com.harriol.baiyishop.search.config.SearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 消费幂等（docs/database.md 9.3）。
 * <p>search-service 没有自己的 MySQL schema，按文档约定用 Redis 去重。
 * <p>Redis 不可用时**不拦截**：索引写入本身按 productId 覆盖，是幂等的；
 * 宁可多写一次，也不能因为去重组件故障丢掉索引更新。
 */
@Service
public class ConsumeDedupService {

    private static final Logger log = LoggerFactory.getLogger(ConsumeDedupService.class);

    private static final String KEY_PREFIX = "baiyishop:search:consumed:";

    private final StringRedisTemplate redis;
    private final SearchProperties properties;

    public ConsumeDedupService(StringRedisTemplate redis, SearchProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean seen(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + eventId));
        } catch (Exception ex) {
            log.warn("Redis 去重查询失败，按未消费处理 eventId={}", eventId, ex);
            return false;
        }
    }

    public void mark(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + eventId, "1", Duration.ofDays(properties.consumeDedupTtlDays()));
        } catch (Exception ex) {
            log.warn("Redis 去重标记失败 eventId={}", eventId, ex);
        }
    }
}
