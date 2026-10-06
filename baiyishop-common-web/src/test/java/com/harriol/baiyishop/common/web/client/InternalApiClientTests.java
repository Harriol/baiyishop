package com.harriol.baiyishop.common.web.client;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 内部调用客户端（docs/architecture.md 4.1）。
 * <p>重点验证「重试的边界」：只读与显式声明幂等的写可以重试一次，
 * 非幂等写**绝不重试**（否则可能产生第二笔业务），业务错误码原样上抛。
 */
class InternalApiClientTests {

    private static final String BASE = "http://baiyishop-product";
    private static final String PATH = "/internal/products/skus/1";

    private record SkuView(Long skuId) {
    }

    private MockRestServiceServer server;
    private InternalApiClient client;

    private void bind() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InternalApiClient(builder.build(), new ObjectMapper(), "test", BASE);
    }

    private static String okBody() {
        return "{\"code\":0,\"message\":\"success\",\"data\":{\"skuId\":1}}";
    }

    @Test
    @DisplayName("只读调用：链路层失败自动重试一次（覆盖冷启动首次超时）")
    void getRetriesOnceOnIoFailure() {
        bind();
        server.expect(ExpectedCount.times(1), requestTo(BASE + PATH))
                .andRespond(withException(new IOException("读超时")));
        server.expect(ExpectedCount.times(1), requestTo(BASE + PATH))
                .andRespond(withSuccess(okBody(), MediaType.APPLICATION_JSON));

        assertThat(client.get(PATH, SkuView.class).skuId()).isEqualTo(1L);
        server.verify();
    }

    @Test
    @DisplayName("幂等写：链路层失败也重试一次（幂等键由调用方保证）")
    void idempotentPostRetriesOnceOnIoFailure() {
        bind();
        server.expect(ExpectedCount.times(1), requestTo(BASE + PATH))
                .andRespond(withException(new IOException("连接被重置")));
        server.expect(ExpectedCount.times(1), requestTo(BASE + PATH))
                .andRespond(withSuccess(okBody(), MediaType.APPLICATION_JSON));

        assertThat(client.postIdempotent(PATH, null, SkuView.class).skuId()).isEqualTo(1L);
        server.verify();
    }

    @Test
    @DisplayName("非幂等写：链路层失败不重试，直接按「依赖不可用」上报")
    void postDoesNotRetry() {
        bind();
        server.expect(ExpectedCount.times(1), requestTo(BASE + PATH))
                .andRespond(withException(new IOException("读超时")));

        assertThatThrownBy(() -> client.post(PATH, "{}", SkuView.class))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_ERROR);
        server.verify();
    }

    @Test
    @DisplayName("业务错误码原样上抛（库存不足 40001 不会被吞成系统繁忙）")
    void businessErrorCodeIsPropagated() {
        bind();
        server.expect(ExpectedCount.times(1), requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":40001,\"message\":\"库存不足，无法完成操作，SKU：[5001]\",\"data\":null}"));

        assertThatThrownBy(() -> client.postIdempotent(PATH, "{}", SkuView.class))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK)
                .hasMessageContaining("库存不足");
    }

    @Test
    @DisplayName("对方已应答 5xx 时不重试（不是链路抖动），按依赖不可用上报")
    void serverErrorIsNotRetried() {
        bind();
        server.expect(ExpectedCount.times(1), requestTo(BASE + PATH))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.get(PATH, SkuView.class)).isInstanceOf(BizException.class);
        server.verify();
    }
}
