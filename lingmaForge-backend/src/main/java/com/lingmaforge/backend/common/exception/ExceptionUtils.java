package exception;

import emun.ToolErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import Excle.GenerateExcelFile.BusinessException;


/**
 * @author 旧巷里的少年郎
 * 异常工具类
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ExceptionUtils {

    public static <T> Result<T> doExceptionService(Throwable err) {
        try {
            if (err instanceof BusinessException) {
                BusinessException e = (BusinessException) err;
                return new Result<>(e.getErrorCode(), e.getMessage());
            }

            if (err instanceof IllegalArgumentException) {
                return new Result<>(ToolErrorCode.PARAMETER_VALID_NOT_PASS.getCode()
                        , ToolErrorCode.PARAMETER_VALID_NOT_PASS.getDesc());
            }
        } catch (Exception e) {
            log.error("call ExceptionUtil doExceptionService：{}", e);
        }
        log.error("call ExceptionUtil doExceptionService err.getMessage()：{}", err.getMessage());
        return new Result<>(ToolErrorCode.SYSTEM_INNER_ERROR.getCode()
                , ToolErrorCode.SYSTEM_INNER_ERROR.getDesc());
    }

    /**
     * 1.远程服务调用异常处理
     *
     * @param result 接口返回参数
     * @param <T>    返回信息
     */
    public static <T> void exceptionHandler(Result<T> result) {

        if (result == null) {
            throw new BusinessException(ToolErrorCode.INTEGRATION_SYSTEM_INNER_ERROR.getCode(),
                    ToolErrorCode.INTEGRATION_SYSTEM_INNER_ERROR.getDesc());
        }
        if (result.isSuccess()) {
            return;
        }
        throw new BusinessException(result.getErrorCode(),
                result.getErrorMsg());
    }

    /**
     * 2.远程服务调用异常处理
     *
     * @param result 接口返回参数
     * @param <T>    返回信息
     */
    public static <T> void exceptionPrimaryHandler(Result<T> result) {

        if (result == null) {
            throw new BusinessException(ToolErrorCode.INTEGRATION_SYSTEM_INNER_ERROR.getCode(),
                    ToolErrorCode.INTEGRATION_SYSTEM_INNER_ERROR.getDesc());
        }
        if (result.isSuccess()) {
            return;
        }
        throw new BusinessException(result.getPrimaryErrorCode(),
                result.getPrimaryErrorMsg());
    }
}
