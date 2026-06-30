package com.lingmaforge.backend.common.model;

import java.io.Serializable;

/**
 * 创建生成任务后的响应。
 *
 * @param taskId 任务 ID（= SSE streamId）
 */
public record GenerationTaskResponse(String taskId) implements Serializable {

    private static final long serialVersionUID = 1L;
}
