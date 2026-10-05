package com.harriol.baiyishop.order.service;

import com.harriol.baiyishop.order.entity.MqOutbox;
import com.harriol.baiyishop.order.mapper.MqOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单侧本地消息表写入（docs/database.md 9.2）。
 * <p>{@link Propagation#MANDATORY}：必须与业务写在同一个本地事务里 ——
 * 「订单建好了、延时取消消息没记下来」会导致订单永远挂在不付款状态（REQ-704 兜底扫描仍能救，
 * 但那是补偿，不该是第一道防线）。
 */
@Service
public class OrderOutboxService {

    private final MqOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public OrderOutboxService(MqOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * @param deliverAt 计划投递时间；为空表示立即投递
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String tag, String bizKey, Object payload, LocalDateTime deliverAt) {
        MqOutbox row = new MqOutbox();
        row.setEventId(UUID.randomUUID().toString().replace("-", ""));
        row.setTopic(topic);
        row.setTag(tag);
        row.setBizKey(bizKey);
        row.setPayload(objectMapper.writeValueAsString(payload));
        row.setStatus(MqOutbox.STATUS_PENDING);
        row.setRetryCount(0);
        row.setDeliverAt(deliverAt);
        outboxMapper.insert(row);
    }
}
