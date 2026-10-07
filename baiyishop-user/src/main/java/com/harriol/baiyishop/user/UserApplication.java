package com.harriol.baiyishop.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务：账号密码与微信认证、用户资料、收货地址、管理员与 RBAC（REQ-101~106）。
 * <p>模块与端口划分见 docs/architecture.md 2.2。
 */
@SpringBootApplication
@MapperScan("com.harriol.baiyishop.user.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
        System.out.println("————————————————————用户服务启动(*´▽｀)ノノ————————————————————");
    }
}
