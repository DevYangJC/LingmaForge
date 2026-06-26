package com.lingmaforge.backend.model;

/**
 * 生成流水线返回的构建校验结果。
 */
public record BuildResult(
        BuildStatus status,
        String output,
        String error,
        long durationMillis) {
}
