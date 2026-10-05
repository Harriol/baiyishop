package com.harriol.baiyishop.seckill.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动视图（REQ-902）。
 *
 * @param countdownSeconds 距开始（未开始）或距结束（进行中）的秒数，前端据此倒计时
 */
public record ActivityView(Long id, String name, LocalDateTime startTime, LocalDateTime endTime,
                           String status, Long countdownSeconds, List<ActivitySkuView> skus) {
}
