package com.harriol.baiyishop.search.source;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.search.dto.IndexDocView;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 通过内部接口读取索引文档（docs/architecture.md 4.1）。
 * <p>按服务名访问（lb://baiyishop-product），超时按架构约定：连接 500ms / 读取 2000ms。
 * <p>搜索服务不连商品库：跨服务只能走 API，避免两个服务争抢同一份真相（架构 2.6）。
 */
@Component
public class HttpProductIndexSource implements ProductIndexSource {

    private static final String PRODUCT_SERVICE = "http://baiyishop-product";
    private static final int PRODUCT_NOT_FOUND_CODE = ErrorCode.PRODUCT_NOT_FOUND.getCode();

    private final RestClient rest;
    private final ObjectMapper objectMapper;

    public HttpProductIndexSource(RestClient.Builder builder, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(2000));
        this.rest = builder.requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<IndexDocView> fetch(long productId) {
        JsonNode body = get("/internal/products/" + productId + "/index-doc");
        int code = body.get("code").asInt();
        if (code == 0) {
            return Optional.of(objectMapper.convertValue(body.get("data"), IndexDocView.class));
        }
        if (code == PRODUCT_NOT_FOUND_CODE) {
            return Optional.empty();
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "读取商品索引文档失败：code=" + code);
    }

    @Override
    public PageResult<IndexDocView> fetchPage(long page, long size) {
        JsonNode data = get("/internal/products/index-docs?page=" + page + "&size=" + size).get("data");
        List<IndexDocView> list = new ArrayList<>();
        for (JsonNode item : data.get("list")) {
            list.add(objectMapper.convertValue(item, IndexDocView.class));
        }
        return PageResult.of(data.get("page").asLong(), data.get("size").asLong(),
                data.get("total").asLong(), list);
    }

    private JsonNode get(String path) {
        String body;
        try {
            body = rest.get().uri(PRODUCT_SERVICE + path).retrieve().body(String.class);
        } catch (RestClientException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "商品服务暂时不可用：" + ex.getMessage());
        }
        JsonNode json = objectMapper.readTree(body);
        if (json == null || json.get("code") == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "商品服务返回体不合法");
        }
        return json;
    }
}
