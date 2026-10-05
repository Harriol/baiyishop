package com.harriol.baiyishop.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 秒杀活动（docs/database.md 8.1，REQ-901、REQ-902）。 */
@Getter
@Setter
@TableName("seckill_activity")
public class SeckillActivity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_ENDED = "ENDED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** DRAFT / NOT_STARTED / RUNNING / ENDED */
    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
