package com.harriol.baiyishop.seckill.service;

import com.harriol.baiyishop.seckill.dto.SeckillOrderEvent;
import com.harriol.baiyishop.seckill.entity.SeckillRecord;
import com.harriol.baiyishop.seckill.mapper.SeckillRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「落排队记录 + 写下单消息」的写入器。
 * <p>单独成类是为了让 @Transactional 生效（同类内自调用不会走代理）：
 * 这两件事必须在同一个本地事务里，缺一个都会出现「扣了库存但没下单」。
 */
@Service
public class SeckillQueueWriter {

    private final SeckillRecordMapper recordMapper;
    private final SeckillOutboxService outboxService;

    public SeckillQueueWriter(SeckillRecordMapper recordMapper, SeckillOutboxService outboxService) {
        this.recordMapper = recordMapper;
        this.outboxService = outboxService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveQueued(SeckillRecord record, String topic, String tag, SeckillOrderEvent event) {
        recordMapper.insert(record);
        outboxService.append(topic, tag, record.getTicketId(), event);
    }
}
