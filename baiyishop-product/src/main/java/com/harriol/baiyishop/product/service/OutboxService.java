package com.harriol.baiyishop.product.service;

import com.harriol.baiyishop.product.config.ProductMqProperties;
import com.harriol.baiyishop.product.dto.ProductChangedEvent;
import com.harriol.baiyishop.product.entity.MqOutbox;
import com.harriol.baiyishop.product.mapper.MqOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 本地消息表写入（docs/database.md 9.2、docs/adr/ADR-005）。
 * <p>{@link Propagation#MANDATORY} 是刻意的：本方法**必须**在业务事务内被调用，
 * 否则「商品改完了、消息没记下来」就失去了事务发件箱的意义。
 */
@Service
public class OutboxService {

    private final MqOutboxMapper outboxMapper;
    private final ProductMqProperties properties;
    private final ObjectMapper objectMapper;

    public OutboxService(MqOutboxMapper outboxMapper, ProductMqProperties properties, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 记录一次商品变更（新增 / 修改 / 上下架 / 删除），由投递任务异步发往 MQ */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendProductChanged(Long productId, String action) {
        String eventId = UUID.randomUUID().toString().replace("-", "");
        ProductChangedEvent event = new ProductChangedEvent(eventId, productId, action, LocalDateTime.now());

        MqOutbox row = new MqOutbox();
        row.setEventId(eventId);
        row.setTopic(properties.productChangedTopic());
        row.setTag(action);
        row.setBizKey(String.valueOf(productId));
        row.setPayload(objectMapper.writeValueAsString(event));
        row.setStatus(MqOutbox.STATUS_PENDING);
        row.setRetryCount(0);
        outboxMapper.insert(row);
    }
}
