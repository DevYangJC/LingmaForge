package com.lingmaforge.backend.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于在 Service 层主动抛出可预期的业务错误，由 {@link GlobalExceptionHandler} 统一捕获
 * 并转换为 {@link com.lingmaforge.backend.common.api.Result} 返回。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，默认 500。 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAIL.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.FAIL.getCode();
    }
}
