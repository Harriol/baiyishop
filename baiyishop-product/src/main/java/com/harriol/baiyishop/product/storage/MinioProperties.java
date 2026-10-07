package com.harriol.baiyishop.product.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 对象存储配置（REQ-203、REQ-401）。密钥不入库、不进日志，由本地配置或环境变量提供。
 *
 * @param endpoint      MinIO 地址，如 http://localhost:9000
 * @param accessKey     访问密钥
 * @param secretKey     私有密钥
 * @param bucket        桶名；桶不存在时首次上传自动创建，并设为匿名只读（图片要能被 &lt;img&gt; 直接加载）
 * @param publicBaseUrl 对外可访问的地址前缀；留空时用 endpoint（本地开发两者相同，容器部署时可指向网关/域名）
 */
@ConfigurationProperties(prefix = "baiyishop.minio")
public record MinioProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        String accessKey,
        String secretKey,
        @DefaultValue("baiyishop") String bucket,
        String publicBaseUrl) {

    /** 图片对外访问前缀：publicBaseUrl 未配置时退回 endpoint */
    public String baseUrlForPublic() {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank() ? endpoint : publicBaseUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
