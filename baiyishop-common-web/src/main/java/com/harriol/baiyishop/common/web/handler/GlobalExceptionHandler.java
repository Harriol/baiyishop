package com.harriol.baiyishop.common.web.handler;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把异常翻译为统一响应体，**不向前端暴露堆栈**（REQ-1003）。
 * <p>传输层状态码取自 {@link ErrorCode#getHttpStatus()}；业务失败仍可能返回 HTTP 200 + 非 0 code。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按错误码映射 HTTP 状态 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("业务异常 code={} message={}", code.getCode(), ex.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(Result.fail(code.getCode(), ex.getMessage()));
    }

    /** 请求体参数校验失败：@Valid 触发 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        String message = detail.isBlank() ? ErrorCode.PARAM_INVALID.getMessage() : detail;
        log.warn("参数校验失败：{}", message);
        return badRequest(message);
    }

    /** 方法参数校验失败：@Validated 触发 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败：{}", message);
        return badRequest(message.isBlank() ? ErrorCode.PARAM_INVALID.getMessage() : message);
    }

    /** 缺少必填参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "缺少必填参数：" + ex.getParameterName();
        log.warn(message);
        return badRequest(message);
    }

    /** 兜底：不向调用方返回堆栈细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.SYSTEM_ERROR));
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ErrorCode.PARAM_INVALID.getCode(), message));
    }
}
