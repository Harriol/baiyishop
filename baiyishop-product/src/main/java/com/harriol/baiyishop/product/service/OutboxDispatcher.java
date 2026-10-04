package com.harriol.baiyishop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.product.config.ProductMqProperties;
import com.harriol.baiyishop.product.entity.MqOutbox;
import com.harriol.baiyishop.product.mapper.MqOutboxMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表投递任务（docs/architecture.md 5.5）。
 * <p>轮询 PENDING 且到达重试时间的消息，投递成功后置 SENT；失败按指数退避重试，
 * 超过 {@code baiyishop.mq.outbox-max-retry} 后置 FAILED 等人工重放（不静默丢弃消息）。
 * <p>投递失败**不影响**商品保存结果 —— 这正是选事务发件箱而不是双写 ES 的原因（ADR-005）。
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private static final int BATCH_SIZE = 100;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final MqOutboxMapper outboxMapper;
    private final ProductMqProperties properties;
    private final ObjectProvider<RocketMQTemplate> templateProvider;

    public OutboxDispatcher(MqOutboxMapper outboxMapper,
                            ProductMqProperties properties,
                            ObjectProvider<RocketMQTemplate> templateProvider) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.templateProvider = templateProvider;
    }

    @Scheduled(fixedDelayString = "${baiyishop.mq.outbox-poll-interval-ms:2000}")
    public void dispatch() {
        // 未启用 RocketMQ（例如单元测试里排除了 MQ 自动装配）时安静跳过
        RocketMQTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        List<MqOutbox> pending = outboxMapper.selectList(Wrappers.<MqOutbox>lambdaQuery()
                .eq(MqOutbox::getStatus, MqOutbox.STATUS_PENDING)
                .and(w -> w.isNull(MqOutbox::getNextRetryAt)
                        .or().le(MqOutbox::getNextRetryAt, LocalDateTime.now()))
                .orderByAsc(MqOutbox::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (MqOutbox row : pending) {
            send(template, row);
        }
    }

    private void send(RocketMQTemplate template, MqOutbox row) {
        String destination = row.getTag() == null ? row.getTopic() : row.getTopic() + ":" + row.getTag();
        try {
            template.syncSend(destination, row.getPayload());
            MqOutbox sent = new MqOutbox();
            sent.setId(row.getId());
            sent.setStatus(MqOutbox.STATUS_SENT);
            sent.setSentAt(LocalDateTime.now());
            outboxMapper.updateById(sent);
        } catch (Exception ex) {
            int retry = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
            MqOutbox failed = new MqOutbox();
            failed.setId(row.getId());
            failed.setRetryCount(retry);
            if (retry >= properties.outboxMaxRetry()) {
                failed.setStatus(MqOutbox.STATUS_FAILED);
            } else {
                failed.setNextRetryAt(LocalDateTime.now().plus(backoff(retry)));
            }
            outboxMapper.updateById(failed);
            log.warn("消息投递失败 eventId={} retry={} destination={}", row.getEventId(), retry, destination, ex);
        }
    }

    /** 指数退避：5s、10s、20s …… 上限 10 分钟 */
    private Duration backoff(int retry) {
        Duration delay = Duration.ofSeconds(5L << Math.min(retry - 1, 7));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }
}
