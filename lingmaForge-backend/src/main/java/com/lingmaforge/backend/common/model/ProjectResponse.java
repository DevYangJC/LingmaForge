package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.lingmaforge.backend.workbench.entity.ProjectEntity;

/**
 * 项目接口返回的项目视图对象。
 *
 * @param id              项目 ID
 * @param name            项目名称
 * @param description     项目描述
 * @param framework       前端框架类型
 * @param status          项目状态（draft / generating / ready / error）
 * @param lastBuildStatus 最近一次构建状态（PENDING / SUCCESS / FAILED）
 * @param sandboxUrl      沙箱预览地址
 * @param createdAt       创建时间
 * @param updatedAt       最后更新时间
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
        LocalDateTime updatedAt) implements Serializable {

    private static final long serialVersionUID = 1L;

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
