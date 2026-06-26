package com.lingmaforge.backend.model;

import java.time.LocalDateTime;

import com.lingmaforge.backend.entity.ProjectEntity;

/**
 * 项目接口返回的项目视图对象。
 */
public record ProjectResponse(
        Long id,
        String name,
        String description,
        String framework,
        String status,
        String lastBuildStatus,
        String sandboxUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * 由实体转换为视图对象。
     *
     * @param entity 项目实体
     * @return 项目视图对象
     */
    public static ProjectResponse from(ProjectEntity entity) {
        return new ProjectResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getFramework(),
                entity.getStatus(),
                entity.getLastBuildStatus(),
                entity.getSandboxUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
