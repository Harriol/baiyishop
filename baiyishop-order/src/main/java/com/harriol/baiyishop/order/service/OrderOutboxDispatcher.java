package com.harriol.baiyishop.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.order.config.OrderProperties;
import com.harriol.baiyishop.order.entity.MqOutbox;
import com.harriol.baiyishop.order.mapper.MqOutboxMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 本地消息表投递任务（docs/architecture.md 5.3、6.3）。
 * <p>订单的两条消息都是延时消息，用 RocketMQ 5.x 的定时消息按 {@code deliver_at} 发送
 * （固定延时等级覆盖不了 15 分钟 / 7 天）。若发送提前或丢失，超时兜底扫描会补上。
 */
@Component
public class OrderOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxDispatcher.class);

    private static final int BATCH_SIZE = 100;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final MqOutboxMapper outboxMapper;
    private final OrderProperties properties;
    private final ObjectProvider<RocketMQTemplate> templateProvider;

    public OrderOutboxDispatcher(MqOutboxMapper outboxMapper,
                                 OrderProperties properties,
                                 ObjectProvider<RocketMQTemplate> templateProvider) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.templateProvider = templateProvider;
    }

    @Scheduled(fixedDelayString = "${baiyishop.order.outbox-poll-interval-ms:2000}")
    public void dispatch() {
        // 未启用 RocketMQ（例如单元测试排除了 MQ 自动装配）时安静跳过
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
            if (row.getDeliverAt() != null && row.getDeliverAt().isAfter(LocalDateTime.now())) {
                long deliverAtMillis = row.getDeliverAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                template.syncSendDeliverTimeMills(destination, row.getPayload(), deliverAtMillis);
            } else {
                template.syncSend(destination, row.getPayload());
            }
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
            log.warn("消息投递失败 eventId={} topic={} retry={}", row.getEventId(), destination, retry, ex);
        }
    }

    /** 指数退避：5s、10s、20s …… 上限 10 分钟 */
    private Duration backoff(int retry) {
        Duration delay = Duration.ofSeconds(5L << Math.min(retry - 1, 7));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }
}
