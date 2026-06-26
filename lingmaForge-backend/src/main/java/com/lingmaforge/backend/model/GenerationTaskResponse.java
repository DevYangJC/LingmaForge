package com.lingmaforge.backend.model;

/**
 * 创建生成任务后的响应。
 *
 * @param taskId 任务 ID（= SSE streamId）
 */
public record GenerationTaskResponse(String taskId) {
}
