package com.harriol.baiyishop.common.web.client;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.web.filter.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
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

    /** 幂等调用的重试次数（含首次），与架构 4.1「仅对幂等接口开启重试（默认 1 次）」一致 */
    private static final int RETRY_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MILLIS = 200L;

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
        return convert(request(HttpMethod.GET, path, null, Set.of(), true), type);
    }

    public <T> T get(String path, TypeReference<T> type) {
        return convert(request(HttpMethod.GET, path, null, Set.of(), true), type);
    }

    /** 非幂等写：失败不重试（重试可能产生第二笔业务） */
    public <T> T post(String path, Object body, Class<T> type) {
        return convert(request(HttpMethod.POST, path, body, Set.of(), false), type);
    }

    public <T> T post(String path, Object body, TypeReference<T> type) {
        return convert(request(HttpMethod.POST, path, body, Set.of(), false), type);
    }

    /**
     * 幂等写：I/O 层失败（连接被重置、读超时）时**重试一次**（docs/architecture.md 4.1 的约定）。
     * <p>调用方必须保证重试安全 —— 例如库存锁定以 orderNo 为幂等键、秒杀划拨以 batchNo 为幂等键；
     * 拿不准就不要用这个方法。
     * <p>为什么加这个：服务刚启动时首次跨服务调用会撞上冷 JVM（类加载 + Seata 分支注册 + 连接池预热），
     * 2s 读超时容易被撑破，表现为一次「依赖服务暂时不可用」。
     */
    public <T> T postIdempotent(String path, Object body, Class<T> type) {
        return convert(request(HttpMethod.POST, path, body, Set.of(), true), type);
    }

    /** 业务码命中 {@code emptyCodes} 时返回 empty（例如商品已删除返回 30007） */
    public <T> Optional<T> getOptional(String path, Class<T> type, int... emptyCodes) {
        return Optional.ofNullable(convert(
                request(HttpMethod.GET, path, null, Set.of(boxed(emptyCodes)), true), type));
    }

    private static Integer[] boxed(int[] codes) {
        Integer[] boxed = new Integer[codes.length];
        for (int i = 0; i < codes.length; i++) {
            boxed[i] = codes[i];
        }
        return boxed;
    }

    /**
     * 返回 data 节点；业务码非 0 且不在 emptyCodes 内时抛业务异常。
     *
     * @param retryable 是否允许对 I/O 层失败重试一次（只读与幂等写为 true）
     */
    private JsonNode request(HttpMethod method, String path, Object body, Set<Integer> emptyCodes,
                             boolean retryable) {
        String raw;
        int attempts = retryable ? RETRY_ATTEMPTS : 1;
        for (int attempt = 1; ; attempt++) {
            try {
                raw = send(method, path, body);
                break;
            } catch (ResourceAccessException ex) {
                // 连接被重置 / 读超时：属于链路层失败，幂等调用可以重试
                if (attempt >= attempts) {
                    log.warn("内部调用失败 {} {}{}（已尝试 {} 次）", method, serviceBase, path, attempt, ex);
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "依赖服务暂时不可用，请稍后重试");
                }
                log.warn("内部调用 {} {}{} 第 {} 次失败，重试一次：{}", method, serviceBase, path, attempt,
                        ex.getMessage());
                sleepBeforeRetry();
            } catch (RestClientException ex) {
                // 对方已应答（4xx/5xx）：不是链路抖动，重试没有意义
                log.warn("内部调用失败 {} {}{}", method, serviceBase, path, ex);
                throw new BizException(ErrorCode.SYSTEM_ERROR, "依赖服务暂时不可用，请稍后重试");
            }
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

    private String send(HttpMethod method, String path, Object body) {
        RestClient.RequestBodySpec spec = rest.method(method).uri(serviceBase + path)
                .header(CALLER_HEADER, caller)
                // 全局事务 XID：让被调方加入同一个 Seata 全局事务（ADR-002）
                .headers(SeataXidPropagator::propagate);
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        if (traceId != null && !traceId.isBlank()) {
            spec = spec.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }
        return body == null ? spec.retrieve().body(String.class)
                : spec.body(body).retrieve().body(String.class);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T convert(JsonNode data, Class<T> type) {
        return data == null || data.isNull() ? null : objectMapper.convertValue(data, type);
    }

    private <T> T convert(JsonNode data, TypeReference<T> type) {
        return data == null || data.isNull() ? null : objectMapper.convertValue(data, type);
    }
}
