package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.lingmaforge.backend.workbench.entity.ChatMessageEntity;

/**
 * 对话消息接口返回的视图对象。
 *
 * @param id        消息唯一标识
 * @param role      消息角色：user / assistant
 * @param content   消息内容
 * @param createdAt 创建时间
 */
public record ChatMessageResponse(
        Long id,
        String role,
        String content,
        LocalDateTime createdAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 由实体转换为视图对象。
     *
     * @param entity 消息实体
     * @return 消息视图对象
     */
    public static ChatMessageResponse from(ChatMessageEntity entity) {
        return new ChatMessageResponse(
                entity.getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt());
    }
}
