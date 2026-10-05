package com.harriol.baiyishop.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.order.client.ProductClient;
import com.harriol.baiyishop.order.client.SeckillClient;
import com.harriol.baiyishop.order.client.UserClient;
import com.harriol.baiyishop.order.dto.AddressSnapshot;
import com.harriol.baiyishop.order.dto.SeckillOrderEventView;
import com.harriol.baiyishop.order.dto.SeckillResultRequest;
import com.harriol.baiyishop.order.dto.SkuSnapshot;
import com.harriol.baiyishop.order.entity.Order;
import com.harriol.baiyishop.order.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 秒杀订单的异步落单（REQ-903、docs/architecture.md 5.4）。
 * <p>消息来自 seckill-service（Redis 预扣成功后经本地消息表投递），本方法负责：
 * 幂等判重 → 取默认收货地址 → 建单并扣减秒杀池（全局事务）→ 回写结果给 seckill。
 * <p>失败语义：
 * <ul>
 *   <li>可预期失败（没有收货地址、商品下架、池子被扣完）：**回写失败原因**，让秒杀侧立刻回补预扣（REQ-904）</li>
 *   <li>不可预期失败（依赖抖动、数据库异常）：抛出 → 交给 RocketMQ 重试；
 *       重试仍失败则由 seckill 的对账任务按 ticketId 兜底（ADR-008 第 5 条）</li>
 * </ul>
 */
@Service
public class SeckillOrderService {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderService.class);

    private static final String FAIL_NO_ADDRESS = "NO_ADDRESS";

    private final OrderMapper orderMapper;
    private final OrderCreator orderCreator;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final SeckillClient seckillClient;

    public SeckillOrderService(OrderMapper orderMapper,
                               OrderCreator orderCreator,
                               UserClient userClient,
                               ProductClient productClient,
                               SeckillClient seckillClient) {
        this.orderMapper = orderMapper;
        this.orderCreator = orderCreator;
        this.userClient = userClient;
        this.productClient = productClient;
        this.seckillClient = seckillClient;
    }

    public void handle(SeckillOrderEventView event) {
        Order existing = byTicket(event.ticketId());
        if (existing != null) {
            log.info("票据已落单，重写结果即可 ticketId={} orderNo={}", event.ticketId(), existing.getOrderNo());
            seckillClient.writeResult(event.ticketId(), SeckillResultRequest.success(existing.getOrderNo()));
            return;
        }

        AddressSnapshot address = userClient.defaultAddress(event.userId()).orElse(null);
        if (address == null) {
            // 秒杀下单没带地址：没有默认地址就明确失败，而不是生成一笔没法发货的订单
            seckillClient.writeResult(event.ticketId(),
                    SeckillResultRequest.failed(FAIL_NO_ADDRESS, "请先设置收货地址"));
            return;
        }

        SkuSnapshot sku;
        try {
            sku = productClient.sku(event.skuId());
        } catch (BizException ex) {
            seckillClient.writeResult(event.ticketId(),
                    SeckillResultRequest.failed("SYSTEM_ERROR", "商品不可售："
                            + ex.getErrorCode().getMessage()));
            return;
        }
        if (!sku.sellable()) {
            seckillClient.writeResult(event.ticketId(),
                    SeckillResultRequest.failed("SYSTEM_ERROR", "商品已下架"));
            return;
        }

        try {
            Order order = orderCreator.createSeckillOrder(event, sku, address);
            seckillClient.writeResult(event.ticketId(), SeckillResultRequest.success(order.getOrderNo()));
        } catch (DataIntegrityViolationException ex) {
            // 同一票据并发落单：uk_seckill_ticket 挡住，返回已存在的那笔
            Order created = byTicket(event.ticketId());
            if (created == null) {
                throw ex;
            }
            seckillClient.writeResult(event.ticketId(), SeckillResultRequest.success(created.getOrderNo()));
        } catch (BizException ex) {
            // 池子被扣完（40001）等可预期失败：回写原因，让秒杀侧回补 Redis 预扣
            log.warn("秒杀落单业务失败 ticketId={} code={} message={}", event.ticketId(),
                    ex.getErrorCode().getCode(), ex.getMessage());
            seckillClient.writeResult(event.ticketId(),
                    SeckillResultRequest.failed(ErrorCode.INSUFFICIENT_STOCK.getCode() == ex.getErrorCode().getCode()
                            ? "SOLD_OUT" : "SYSTEM_ERROR", ex.getMessage()));
        }
    }

    private Order byTicket(String ticketId) {
        return orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getSeckillTicketId, ticketId));
    }
}
