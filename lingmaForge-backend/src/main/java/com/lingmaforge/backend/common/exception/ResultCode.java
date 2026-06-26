package com.lingmaforge.backend.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用业务状态码枚举。
 *
 * <p>集中管理常用状态码，避免在代码中出现魔法数字。</p>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /** 成功。 */
    SUCCESS(200, "ok"),
    /** 通用失败。 */
    FAIL(500, "服务异常"),
    /** 参数校验失败。 */
    PARAM_INVALID(400, "参数校验失败"),
    /** 资源不存在。 */
    NOT_FOUND(404, "资源不存在"),
    /** 生成任务未找到。 */
    TASK_NOT_FOUND(40401, "生成任务不存在"),
    /** 生成任务正在运行，禁止重复操作。 */
    TASK_RUNNING(40901, "生成任务正在运行"),
    /** 项目不存在。 */
    PROJECT_NOT_FOUND(40402, "项目不存在"),
    /** 文件不存在。 */
    FILE_NOT_FOUND(40403, "文件不存在");

    /** 状态码。 */
    private final int code;

    /** 提示信息。 */
    private final String message;
}
