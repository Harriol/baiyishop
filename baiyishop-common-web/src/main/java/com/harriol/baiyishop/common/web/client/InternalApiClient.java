package com.harriol.baiyishop.common.web.client;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.web.filter.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;

/**
 * 内部服务调用客户端（docs/architecture.md 4.1）。
 * <p>统一处理三件事，避免每个服务各写一遍：
 * <ol>
 *   <li>按服务名寻址（{@code http://baiyishop-xxx}）+ 透传 traceId / X-Caller</li>
 *   <li>拆 {@code {code,message,data}} 信封</li>
 *   <li>业务码**原样上抛**：上游返回 40001 就抛 40001，调用方的前端看到的仍是准确的业务原因，
 *       而不是笼统的「系统繁忙」</li>
 * </ol>
 * <p>只读调用失败一律转成业务异常（不做静默降级）：内部链路读不到商品 / 库存时，
 * 继续往下走只会产生错单。
 */
public class InternalApiClient {

    private static final Logger log = LoggerFactory.getLogger(InternalApiClient.class);

    public static final String CALLER_HEADER = "X-Caller";

    private final RestClient rest;
    private final ObjectMapper objectMapper;
    private final String caller;
    private final String serviceBase;

    InternalApiClient(RestClient rest, ObjectMapper objectMapper, String caller, String serviceBase) {
        this.rest = rest;
        this.objectMapper = objectMapper;
        this.caller = caller;
        this.serviceBase = serviceBase;
    }

    public <T> T get(String path, Class<T> type) {
        return convert(request(HttpMethod.GET, path, null), type);
    }

    public <T> T get(String path, TypeReference<T> type) {
        return convert(request(HttpMethod.GET, path, null), type);
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return convert(request(HttpMethod.POST, path, body), type);
    }

    public <T> T post(String path, Object body, TypeReference<T> type) {
        return convert(request(HttpMethod.POST, path, body), type);
    }

    /** 业务码命中 {@code emptyCodes} 时返回 empty（例如商品已删除返回 30007） */
    public <T> Optional<T> getOptional(String path, Class<T> type, int... emptyCodes) {
        return Optional.ofNullable(convert(request(HttpMethod.GET, path, null, Set.of(boxed(emptyCodes))), type));
    }

    private static Integer[] boxed(int[] codes) {
        Integer[] boxed = new Integer[codes.length];
        for (int i = 0; i < codes.length; i++) {
            boxed[i] = codes[i];
        }
        return boxed;
    }

    private JsonNode request(HttpMethod method, String path, Object body) {
        return request(method, path, body, Set.of());
    }

    /** 返回 data 节点；业务码非 0 且不在 emptyCodes 内时抛业务异常 */
    private JsonNode request(HttpMethod method, String path, Object body, Set<Integer> emptyCodes) {
        String raw;
        try {
            RestClient.RequestBodySpec spec = rest.method(method).uri(serviceBase + path)
                    .header(CALLER_HEADER, caller);
            String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
            if (traceId != null && !traceId.isBlank()) {
                spec = spec.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
            }
            raw = body == null ? spec.retrieve().body(String.class)
                    : spec.body(body).retrieve().body(String.class);
        } catch (RestClientException ex) {
            log.warn("内部调用失败 {} {}{}", method, serviceBase, path, ex);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "依赖服务暂时不可用，请稍后重试");
        }

        JsonNode json = objectMapper.readTree(raw);
        if (json == null || json.get("code") == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "依赖服务返回体不合法");
        }
        int code = json.get("code").asInt();
        if (code == ErrorCode.SUCCESS.getCode() || emptyCodes.contains(code)) {
            return json.get("data");
        }
        String message = json.get("message") == null ? null : json.get("message").asString();
        throw new BizException(ErrorCode.of(code), message == null ? ErrorCode.of(code).getMessage() : message);
    }

    private <T> T convert(JsonNode data, Class<T> type) {
        return data == null || data.isNull() ? null : objectMapper.convertValue(data, type);
    }

    private <T> T convert(JsonNode data, TypeReference<T> type) {
        return data == null || data.isNull() ? null : objectMapper.convertValue(data, type);
    }
}
