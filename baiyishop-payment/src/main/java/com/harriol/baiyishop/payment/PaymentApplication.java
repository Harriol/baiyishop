package com.harriol.baiyishop.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 支付服务：发起支付、渠道回调验签与支付结果查询（REQ-801~803）。
 * <p>模块与端口划分见 docs/architecture.md 2.2。
 */
@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
        System.out.println("支付服务启动(*´▽｀)ノノ");
    }
}
