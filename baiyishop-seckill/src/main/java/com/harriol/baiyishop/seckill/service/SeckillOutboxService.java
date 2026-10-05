package com.harriol.baiyishop.seckill.service;

import com.harriol.baiyishop.seckill.entity.MqOutbox;
import com.harriol.baiyishop.seckill.mapper.MqOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 本地消息表写入（docs/database.md 9.2、ADR-008 第 3 条）。
 * <p>必须与「抢购记录落 QUEUED」同事务：预扣成功了但下单消息没记下来，用户就白抢了。
 */
@Service
public class SeckillOutboxService {

    private final MqOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public SeckillOutboxService(MqOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String tag, String bizKey, Object payload) {
        MqOutbox row = new MqOutbox();
        row.setEventId(UUID.randomUUID().toString().replace("-", ""));
        row.setTopic(topic);
        row.setTag(tag);
        row.setBizKey(bizKey);
        row.setPayload(objectMapper.writeValueAsString(payload));
        row.setStatus(MqOutbox.STATUS_PENDING);
        row.setRetryCount(0);
        outboxMapper.insert(row);
    }
}
