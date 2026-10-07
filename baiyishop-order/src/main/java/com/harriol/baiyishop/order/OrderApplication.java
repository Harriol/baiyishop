package com.harriol.baiyishop.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 订单服务：购物车、订单状态机、超时取消与自动确认收货（REQ-601~602、REQ-701~708）。
 * <p>模块与端口划分见 docs/architecture.md 2.2。
 */
@SpringBootApplication
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
        System.out.println("————————————————————订单服务启动(*´▽｀)ノノ————————————————————");
    }
}
