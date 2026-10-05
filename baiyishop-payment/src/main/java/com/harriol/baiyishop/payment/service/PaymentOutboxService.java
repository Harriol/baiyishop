package com.harriol.baiyishop.payment.service;

import com.harriol.baiyishop.payment.entity.MqOutbox;
import com.harriol.baiyishop.payment.mapper.MqOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 本地消息表写入（docs/database.md 9.2）。
 * <p>必须与「支付单置为成功」在同一个本地事务里：支付成功了但事件没记下来，订单会永远停在待付款，而用户的钱已经付了。
 */
@Service
public class PaymentOutboxService {

    private final MqOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public PaymentOutboxService(MqOutboxMapper outboxMapper, ObjectMapper objectMapper) {
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
