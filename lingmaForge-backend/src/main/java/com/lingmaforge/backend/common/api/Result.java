package com.lingmaforge.backend.common.api;

import java.io.Serializable;

import lombok.Getter;

/**
 * 全局统一返回对象。
 *
 * <p>所有 RESTful 接口必须返回 {@code Result&lt;T&gt;}，不直接返回实体或集合。
 * 采用阿里嵩山版规范：包含业务状态码、数据载体与提示信息。</p>
 *
 * @param <T> 业务数据类型
 */
@Getter
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功状态码。 */
    public static final int CODE_SUCCESS = 200;

    /** 失败状态码（业务异常）。 */
    public static final int CODE_FAIL = 500;

    /** 业务状态码：200 成功，其它表示失败。 */
    private final int code;

    /** 业务数据载体。 */
    private final T data;

    /** 提示信息，成功时为 "ok"，失败时为错误描述。 */
    private final String message;

    /** 是否成功，便于前端直接判断。 */
    private final boolean success;

    private Result(int code, T data, String message, boolean success) {
        this.code = code;
        this.data = data;
        this.message = message;
        this.success = success;
    }

    /**
     * 构造成功结果。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_SUCCESS, data, "ok", true);
    }

    /**
     * 构造成功结果并附带自定义提示。
     *
     * @param data    业务数据
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(CODE_SUCCESS, data, message, true);
    }

    /**
     * 构造失败结果。
     *
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(CODE_FAIL, null, message, false);
    }

    /**
     * 构造失败结果并指定状态码。
     *
     * @param code    状态码
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, null, message, false);
    }
}
