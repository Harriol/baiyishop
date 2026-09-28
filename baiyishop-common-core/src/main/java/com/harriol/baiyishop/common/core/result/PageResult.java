package com.harriol.baiyishop.common.core.result;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页结构（docs/api.md 1.4）：{@code {"page":1,"size":10,"total":57,"list":[]}}。
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long page;
    private long size;
    private long total;
    private List<T> list;

    public PageResult() {
        this.list = Collections.emptyList();
    }

    public PageResult(long page, long size, long total, List<T> list) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.list = list == null ? Collections.emptyList() : list;
    }

    public static <T> PageResult<T> of(long page, long size, long total, List<T> list) {
        return new PageResult<>(page, size, total, list);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(page, size, 0L, Collections.emptyList());
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list == null ? Collections.emptyList() : list;
    }
}
