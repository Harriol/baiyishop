package com.harriol.baiyishop.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 库存服务：普通库存与秒杀库存池、锁定扣减释放、库存流水与预警（REQ-501~505）。
 * <p>模块与端口划分见 docs/architecture.md 2.2。
 */
@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
        System.out.println("————————————————————库存服务启动(*´▽｀)ノノ————————————————————");
    }
}
