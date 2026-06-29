package com.lingmaforge.backend.common.exception;

import com.lingmaforge.backend.common.api.Result;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 异常工具类。
 *
 * <p>提供统一的异常转 {@link Result} 方法，以及远程调用结果校验。</p>
 *
 * @author 旧巷里的少年郎
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ExceptionUtils {

    /**
     * 将捕获的 Throwable 转换为统一的失败 Result。
     *
     * <ul>
     *   <li>{@link BusinessException} → 取其 code + message</li>
     *   <li>{@link IllegalArgumentException} → PARAM_INVALID</li>
     *   <li>其它异常 → FAIL 兜底</li>
     * </ul>
     *
     * @param err 捕获到的异常
     * @param <T> Result 中的数据类型
     * @return 统一的失败 Result
     */
    public static <T> Result<T> doExceptionService(Throwable err) {
        try {
            if (err instanceof BusinessException e) {
                return Result.fail(e.getCode(), e.getMessage());
            }
            if (err instanceof IllegalArgumentException) {
                return Result.fail(ResultCode.PARAM_INVALID.getCode(),
                        ResultCode.PARAM_INVALID.getMessage());
            }
        } catch (Exception e) {
            log.error("ExceptionUtils.doExceptionService 内部异常: {}", e.getMessage(), e);
        }
        log.error("未分类异常: {}", err.getMessage(), err);
        return Result.fail(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMessage());
    }

    /**
     * 校验远程服务返回的 Result，非成功时抛出 BusinessException。
     *
     * @param result 远程服务返回结果
     * @param <T>    数据类型
     */
    public static <T> void exceptionHandler(Result<T> result) {
        if (result == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "远程服务返回 null");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
    }
}
