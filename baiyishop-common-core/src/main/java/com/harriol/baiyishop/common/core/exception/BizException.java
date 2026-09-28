package com.harriol.baiyishop.common.core.exception;

import com.harriol.baiyishop.common.core.result.ErrorCode;

/**
 * 业务异常。由全局异常处理器翻译为统一响应体，不向前端暴露堆栈（REQ-1003）。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
