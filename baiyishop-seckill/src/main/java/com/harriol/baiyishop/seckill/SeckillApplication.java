package com.harriol.baiyishop.seckill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀服务：活动配置、Redis 预扣、限购校验、异步下单与结果查询（REQ-901~906）。
 * <p>模块与端口划分见 docs/architecture.md 2.2。
 */
@SpringBootApplication
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
        System.out.println("————————————————————秒杀服务启动(*´▽｀)ノノ————————————————————");
    }
}
