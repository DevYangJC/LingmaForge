package com.lingmaforge.backend.common.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.lingmaforge.backend.common.api.Result;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理器。
 *
 * <p>使用 {@link RestControllerAdvice} 统一捕获控制器抛出的异常，转换为 {@link Result} 格式返回，
 * 并记录异常日志。捕获范围包括：业务异常、参数校验异常、参数类型不匹配异常及其它未捕获异常。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常
     * @return 统一失败结果
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.OK).body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理 @RequestBody 参数校验异常。
     *
     * @param ex 校验异常
     * @return 统一失败结果，message 含所有字段错误
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.PARAM_INVALID.getCode(), message));
    }

    /**
     * 处理 Query / Path 参数校验异常（@Validated）。
     *
     * @param ex 约束违反异常
     * @return 统一失败结果
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.PARAM_INVALID.getCode(), message));
    }

    /**
     * 处理参数类型不匹配异常。
     *
     * @param ex 类型不匹配异常
     * @return 统一失败结果
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "参数类型不匹配: " + ex.getName();
        log.warn(message);
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.PARAM_INVALID.getCode(), message));
    }

    /**
     * 兜底处理其它未捕获异常。
     *
     * @param ex 未捕获异常
     * @return 统一失败结果
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.FAIL.getCode(), "系统异常，请稍后重试"));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
