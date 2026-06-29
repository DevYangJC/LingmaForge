package com.lingmaforge.backend.model;

/**
 * 构建生命周期状态值。
 */
public enum BuildStatus {
    /** 等待构建 */
    PENDING,
    /** 构建成功 */
    SUCCESS,
    /** 构建失败 */
    FAILED
}
