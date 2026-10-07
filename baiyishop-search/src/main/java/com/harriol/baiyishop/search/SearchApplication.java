package com.harriol.baiyishop.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 搜索服务：Elasticsearch 检索、筛选排序与索引同步（REQ-301~302）。
 * <p>模块与端口划分见 docs/architecture.md 2.2。
 */
@SpringBootApplication
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
        System.out.println("搜索服务启动(*´▽｀)ノノ");
    }
}
