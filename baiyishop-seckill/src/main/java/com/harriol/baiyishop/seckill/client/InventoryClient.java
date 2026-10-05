package com.harriol.baiyishop.seckill.client;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.seckill.dto.AllocateStockRequest;
import com.harriol.baiyishop.seckill.dto.InventoryOpResult;
import com.harriol.baiyishop.seckill.dto.ReturnStockRequest;
import com.harriol.baiyishop.seckill.dto.SeckillPoolView;
import org.springframework.stereotype.Component;

/**
 * 库存域内部调用（ADR-004：秒杀池归 inventory 所有）。
 * <p>划拨 / 回补都带 batchNo 参与幂等键，重试不会重复划拨（REQ-504）。
 */
@Component
public class InventoryClient {

    private static final String SERVICE = "http://baiyishop-inventory";

    private final InternalApiClient client;

    public InventoryClient(InternalApiClients clients) {
        this.client = clients.client(SERVICE);
    }

    /** 划拨普通库存到秒杀池：划拨即扣减普通库存（REQ-504） */
    public SeckillPoolView allocate(AllocateStockRequest request) {
        return client.post("/internal/inventory/seckill/allocate", request, SeckillPoolView.class);
    }

    /** 回补：UNSOLD 活动结束回补普通库存 / ROLLBACK 取消回滚到秒杀池 */
    public SeckillPoolView returnStock(ReturnStockRequest request) {
        return client.post("/internal/inventory/seckill/return", request, SeckillPoolView.class);
    }

    /** 秒杀池现状（展示剩余量 / 对账用） */
    public SeckillPoolView pool(long activitySkuId) {
        return client.get("/internal/inventory/seckill/pool/" + activitySkuId, SeckillPoolView.class);
    }

    /** 对账用：池子不存在时返回 empty（按 activitySkuId 查不到由 inventory 返回 40004） */
    public SeckillPoolView poolOrNull(long activitySkuId) {
        try {
            return pool(activitySkuId);
        } catch (BizException ex) {
            if (ex.getErrorCode() == ErrorCode.SECKILL_STOCK_POOL_NOT_FOUND) {
                return null;
            }
            throw ex;
        }
    }
}
