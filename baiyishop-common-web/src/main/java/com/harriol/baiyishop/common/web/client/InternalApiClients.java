package com.harriol.baiyishop.common.web.client;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 内部调用客户端工厂：一个服务里为每个依赖服务建一个客户端实例。
 * <p>超时按架构 4.1 的约定：连接 500ms / 读取 2000ms（可用
 * {@code baiyishop.internal.connect-timeout-ms} / {@code read-timeout-ms} 覆盖；
 * 秒杀链路的调用方另设更短阈值）。
 */
public class InternalApiClients {

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;
    private final String caller;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public InternalApiClients(RestClient.Builder builder, ObjectMapper objectMapper, String caller,
                              Duration connectTimeout, Duration readTimeout) {
        this.builder = builder;
        this.objectMapper = objectMapper;
        this.caller = caller;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * @param serviceUrl 服务名形式的基址，如 {@code http://baiyishop-product}
     */
    public InternalApiClient client(String serviceUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        RestClient rest = builder.clone().requestFactory(factory).build();
        return new InternalApiClient(rest, objectMapper, caller, serviceUrl);
    }
}
