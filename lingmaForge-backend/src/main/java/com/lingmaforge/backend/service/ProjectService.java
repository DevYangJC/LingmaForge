package com.lingmaforge.backend.service;

import java.nio.file.Path;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lingmaforge.backend.entity.ProjectEntity;
import com.lingmaforge.backend.model.CreateProjectRequest;
import com.lingmaforge.backend.model.ProjectContext;

/**
 * 项目领域服务契约。
 *
 * <p>负责项目 CRUD、工作区磁盘目录管理以及项目上下文读取。</p>
 */
public interface ProjectService extends IService<ProjectEntity> {

    /**
     * 创建项目并初始化工作区目录。
     *
     * @param request 项目创建请求
     * @return 创建后的项目实体
     */
    ProjectEntity createProject(CreateProjectRequest request);

    /**
     * 获取项目工作区根目录的绝对路径。
     *
     * @param projectId 项目 ID
     * @return 工作区目录路径
     */
    Path getProjectWorkspace(Long projectId);

    /**
     * 读取项目上下文，包括框架、已有文件列表、package.json 依赖。
     *
     * @param projectId 项目 ID
     * @return 项目上下文快照
     */
    ProjectContext getProjectContext(Long projectId);

    /**
     * 更新项目的构建状态与沙箱地址。
     *
     * @param projectId      项目 ID
     * @param buildStatus    构建状态
     * @param sandboxUrl     沙箱预览 URL
     */
    void updateBuildResult(Long projectId, String buildStatus, String sandboxUrl);
}
