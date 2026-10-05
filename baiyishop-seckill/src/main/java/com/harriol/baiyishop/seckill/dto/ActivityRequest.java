package com.harriol.baiyishop.seckill.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 后台创建 / 修改秒杀活动（REQ-901）。 */
public record ActivityRequest(String name, LocalDateTime startTime, LocalDateTime endTime,
                              List<ActivitySkuRequest> skus) {
}
