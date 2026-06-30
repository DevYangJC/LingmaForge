package com.lingmaforge.backend.common.model;

import java.io.Serializable;

import com.lingmaforge.backend.workbench.entity.GenerationTaskEntity;

/**
 * 生成任务状态响应。
 *
 * @param taskId       任务 ID（= SSE streamId）
 * @param status       任务状态：running / completed / failed / stopped
 * @param currentStage 当前阶段：requirement_analysis / iteration / ...
 * @param buildTime    构建耗时（秒）
 * @param previewUrl   沙箱预览地址
 * @param errorMessage 错误信息，完成时为空
 */
public record GenerationTaskStatusResponse(
        String taskId,
        String status,
        String currentStage,
        Integer buildTime,
        String previewUrl,
        String errorMessage) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 由实体转换为响应对象。
     *
     * @param entity 生成任务实体
     * @return 任务状态响应
     */
    public static GenerationTaskStatusResponse from(GenerationTaskEntity entity) {
        return new GenerationTaskStatusResponse(
                entity.getTaskId(),
                entity.getStatus(),
                entity.getCurrentStage(),
                entity.getBuildTime(),
                entity.getPreviewUrl(),
                entity.getErrorMessage());
    }
}
