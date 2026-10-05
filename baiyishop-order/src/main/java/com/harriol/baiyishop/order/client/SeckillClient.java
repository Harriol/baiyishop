package com.harriol.baiyishop.order.client;

import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.order.dto.SeckillResultRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 秒杀域内部调用（docs/api.md 第 6 章）。
 * <p>回写结果**失败不影响订单**：订单已落库，票据状态由 seckill 的对账任务按 ticketId 回查补齐
 * （ADR-008 第 5 条），所以这里只记录日志、不抛异常。
 */
@Component
public class SeckillClient {

    private static final Logger log = LoggerFactory.getLogger(SeckillClient.class);

    private static final String SERVICE = "http://baiyishop-seckill";

    private final InternalApiClient client;

    public SeckillClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    public void writeResult(String ticketId, SeckillResultRequest request) {
        try {
            client.post("/internal/seckill/records/" + ticketId + "/result", request, Void.class);
        } catch (RuntimeException ex) {
            log.warn("回写抢购结果失败，等待 seckill 侧对账补齐 ticketId={} status={}", ticketId, request.status(), ex);
        }
    }
}
