package com.lingmaforge.backend.common.model;

import java.io.Serializable;

/**
 * 生成流水线返回的构建校验结果。
 *
 * @param status        构建状态（PENDING / SUCCESS / FAILED）
 * @param output        构建的标准输出
 * @param error         构建失败时的错误信息，成功时为 {@code null}
 * @param durationMillis 构建耗时（毫秒）
 */
public record BuildResult(
        BuildStatus status,
        String output,
        String error,
        long durationMillis) implements Serializable {

    private static final long serialVersionUID = 1L;
}
