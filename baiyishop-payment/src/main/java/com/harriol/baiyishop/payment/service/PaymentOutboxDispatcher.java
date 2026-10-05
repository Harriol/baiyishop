package com.harriol.baiyishop.payment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.payment.config.PaymentProperties;
import com.harriol.baiyishop.payment.entity.MqOutbox;
import com.harriol.baiyishop.payment.mapper.MqOutboxMapper;
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
 * 本地消息表投递任务：把「支付成功」事件发给 order-service（docs/architecture.md 5.2）。
 * <p>不要求顺序消息：消费端是按订单状态条件更新的，天然幂等，所以用普通发送 + 指数退避重试。
 */
@Component
public class PaymentOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxDispatcher.class);

    private static final int BATCH_SIZE = 100;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final MqOutboxMapper outboxMapper;
    private final PaymentProperties properties;
    private final ObjectProvider<RocketMQTemplate> templateProvider;

    public PaymentOutboxDispatcher(MqOutboxMapper outboxMapper,
                                   PaymentProperties properties,
                                   ObjectProvider<RocketMQTemplate> templateProvider) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.templateProvider = templateProvider;
    }

    @Scheduled(fixedDelayString = "${baiyishop.payment.outbox-poll-interval-ms:2000}")
    public void dispatch() {
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
                log.error("支付成功事件投递失败超限 eventId={} bizKey={}", row.getEventId(), row.getBizKey(), ex);
            } else {
                failed.setNextRetryAt(LocalDateTime.now().plus(backoff(retry)));
                log.warn("支付成功事件投递失败 eventId={} retry={}", row.getEventId(), retry, ex);
            }
            outboxMapper.updateById(failed);
        }
    }

    private Duration backoff(int retry) {
        Duration delay = Duration.ofSeconds(5L << Math.min(retry - 1, 7));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }
}
