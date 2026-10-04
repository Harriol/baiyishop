package com.harriol.baiyishop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权白名单。格式 {@code 方法:路径} 或仅 {@code 路径}（不限方法），支持 Ant 风格通配。
 * <p>放在配置里而不是写死在代码中，便于按环境调整（docs/architecture.md 1.3 配置与代码分离）。
 */
@ConfigurationProperties(prefix = "baiyishop.gateway")
public class GatewayAuthProperties {

    /** 公开路径：无需令牌即可访问（docs/api.md 2.1） */
    private List<String> publicPaths = new ArrayList<>();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}