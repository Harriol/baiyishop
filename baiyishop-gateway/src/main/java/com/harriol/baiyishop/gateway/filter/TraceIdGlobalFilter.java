package com.harriol.baiyishop.gateway.filter;

import com.harriol.baiyishop.common.security.SecurityConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全链路 traceId：网关统一生成并向下游与响应回传，服务端只消费不再生成（docs/architecture.md 11.1）。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        exchange.getResponse().getHeaders().set(SecurityConstants.HEADER_TRACE_ID, traceId);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(SecurityConstants.HEADER_TRACE_ID, traceId)
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}