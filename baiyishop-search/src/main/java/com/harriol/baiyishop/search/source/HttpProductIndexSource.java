package com.harriol.baiyishop.search.source;

import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import com.harriol.baiyishop.search.dto.IndexDocView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 通过内部接口读取索引文档（docs/architecture.md 4.1）。
 * <p>按服务名访问（http://baiyishop-product），超时与信封解析交给公共客户端
 * {@link InternalApiClients}（连接 500ms / 读取 2000ms）。
 * <p>搜索服务不连商品库：跨服务只能走 API，避免两个服务争抢同一份真相（架构 2.6）。
 */
@Component
public class HttpProductIndexSource implements ProductIndexSource {

    private static final String PRODUCT_SERVICE = "http://baiyishop-product";
    private static final int PRODUCT_NOT_FOUND_CODE = ErrorCode.PRODUCT_NOT_FOUND.getCode();

    private final InternalApiClient client;

    public HttpProductIndexSource(InternalApiClients clients) {
        this.client = clients.client(PRODUCT_SERVICE);
    }

    @Override
    public Optional<IndexDocView> fetch(long productId) {
        return client.getOptional("/internal/products/" + productId + "/index-doc",
                IndexDocView.class, PRODUCT_NOT_FOUND_CODE);
    }

    @Override
    public PageResult<IndexDocView> fetchPage(long page, long size) {
        PageSlice slice = client.get("/internal/products/index-docs?page=" + page + "&size=" + size,
                PageSlice.class);
        if (slice == null) {
            return PageResult.empty(page, size);
        }
        return PageResult.of(slice.page(), slice.size(), slice.total(),
                slice.list() == null ? List.of() : slice.list());
    }

    /** 内部接口的分页信封（docs/api.md 1.4）：用独立的 record 承接，避免泛型擦除带来的转换问题 */
    private record PageSlice(long page, long size, long total, List<IndexDocView> list) {
    }
}
