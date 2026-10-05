package com.harriol.baiyishop.seckill.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 活动状态推进（REQ-902）：到点自动开始、过点自动结束并回补未售出库存。
 * <p>购买判定不依赖这个任务（Lua 里会再校验时间），这里只保证前台的展示状态与后台数据一致。
 */
@Component
public class SeckillStatusJob {

    private static final Logger log = LoggerFactory.getLogger(SeckillStatusJob.class);

    private final SeckillActivityService activityService;

    public SeckillStatusJob(SeckillActivityService activityService) {
        this.activityService = activityService;
    }

    @Scheduled(fixedDelayString = "${baiyishop.seckill.status-scan-delay:30s}")
    public void refresh() {
        try {
            activityService.refreshRunningStatus();
        } catch (Exception ex) {
            log.warn("秒杀活动状态推进失败", ex);
        }
    }
}
