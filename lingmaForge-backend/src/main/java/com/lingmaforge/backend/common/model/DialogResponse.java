package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.lingmaforge.backend.workbench.entity.DialogEntity;

/**
 * 会话接口返回的视图对象。
 *
 * @param dialogId  业务会话 ID（= SSE streamId）
 * @param projectId 关联项目 ID，可空（闲聊无项目）
 * @param title     会话标题
 * @param status    会话状态（active / archived）
 * @param createdAt 创建时间
 */
public record DialogResponse(
        String dialogId,
        Long projectId,
        String title,
        String status,
        LocalDateTime createdAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 由实体转换为视图对象。
     *
     * @param entity 会话实体
     * @return 会话视图对象
     */
    public static DialogResponse from(DialogEntity entity) {
        return new DialogResponse(
                entity.getDialogId(),
                entity.getProjectId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getCreatedAt());
    }
}
