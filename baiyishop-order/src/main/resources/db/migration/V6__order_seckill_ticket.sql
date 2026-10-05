-- ============================================================================
-- 订单表增加秒杀票据号（REQ-903 ~ REQ-905）
--
-- 背景：秒杀订单由 seckill-service 异步发起（Redis 预扣成功后投递消息），
-- order-service 落单后需要把结果回写给 seckill。为了让「按票据回查订单」成为一次索引查询
-- （对账任务与结果补偿都要用），订单上直接存票据号，唯一索引保证一张票据只会落一笔订单。
--
-- 非秒杀订单该列为 NULL；MySQL 唯一索引允许多个 NULL，不影响普通订单。
-- ============================================================================

ALTER TABLE `order`
    ADD COLUMN seckill_ticket_id VARCHAR(40) DEFAULT NULL COMMENT '秒杀票据（非秒杀订单为 NULL）' AFTER source,
    ADD UNIQUE KEY uk_seckill_ticket (seckill_ticket_id);
