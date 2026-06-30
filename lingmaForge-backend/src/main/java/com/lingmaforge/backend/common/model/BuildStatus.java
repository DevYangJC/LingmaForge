package com.lingmaforge.backend.common.model;

import java.io.Serializable;

/**
 * 构建生命周期状态值。
 */
public enum BuildStatus implements Serializable {
    /** 等待构建 */
    PENDING,
    /** 构建成功 */
    SUCCESS,
    /** 构建失败 */
    FAILED;

    private static final long serialVersionUID = 1L;
}
