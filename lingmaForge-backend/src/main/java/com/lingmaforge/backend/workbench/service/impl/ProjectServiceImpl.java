package com.lingmaforge.backend.workbench.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.ResultCode;
import com.lingmaforge.backend.workbench.entity.ChatMessageEntity;
import com.lingmaforge.backend.workbench.entity.GenerationTaskEntity;
import com.lingmaforge.backend.workbench.entity.ProjectEntity;
import com.lingmaforge.backend.workbench.entity.ProjectFileEntity;
import com.lingmaforge.backend.workbench.mapper.ChatMessageMapper;
import com.lingmaforge.backend.workbench.mapper.GenerationTaskMapper;
import com.lingmaforge.backend.workbench.mapper.ProjectFileMapper;
import com.lingmaforge.backend.workbench.mapper.ProjectMapper;
import com.lingmaforge.backend.common.model.CreateProjectRequest;
import com.lingmaforge.backend.common.model.ProjectContext;
import com.lingmaforge.backend.common.model.UpdateProjectRequest;
import com.lingmaforge.backend.workbench.service.ProjectService;

/**
 * 基于 MyBatis-Plus 的项目服务实现。
 *
 * <p>负责项目持久化、工作区磁盘目录管理以及项目上下文读取。</p>
 */
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, ProjectEntity> implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private final ProjectFileMapper projectFileMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final Path workspaceRoot;

    public ProjectServiceImpl(ProjectFileMapper projectFileMapper,
            ChatMessageMapper chatMessageMapper,
            GenerationTaskMapper generationTaskMapper,
            @Value("${lingma.workspace-root:./workspace}") String workspaceRoot) {
        this.projectFileMapper = projectFileMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.generationTaskMapper = generationTaskMapper;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public ProjectEntity createProject(CreateProjectRequest request) {
        ProjectEntity project = new ProjectEntity();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setFramework(request.framework() == null ? "react-vite-ts" : request.framework());
        project.setStatus("draft");
        save(project);
        initWorkspace(project.getId());
        log.info("创建项目成功: id={}, name={}", project.getId(), project.getName());
        return project;
    }

    @Override
    public Path getProjectWorkspace(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }
        return workspaceRoot.resolve(String.valueOf(projectId));
    }

    @Override
    public ProjectContext getProjectContext(Long projectId) {
        ProjectEntity project = getById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }
        List<ProjectFileEntity> files = projectFileMapper.selectList(
                new LambdaQueryWrapper<ProjectFileEntity>()
                        .eq(ProjectFileEntity::getProjectId, projectId)
                        .orderByAsc(ProjectFileEntity::getPath));
        List<String> filePaths = files.stream().map(ProjectFileEntity::getPath).toList();
        List<String> dependencies = parseDependencies(files);
        return new ProjectContext(project.getFramework(), filePaths, dependencies);
    }

    @Override
    public void updateBuildResult(Long projectId, String buildStatus, String sandboxUrl) {
        ProjectEntity project = getById(projectId);
        if (project == null) {
            return;
        }
        project.setLastBuildStatus(buildStatus);
        if (sandboxUrl != null) {
            project.setSandboxUrl(sandboxUrl);
        }
        if ("SUCCESS".equals(buildStatus)) {
            project.setStatus("built");
        } else if ("FAILED".equals(buildStatus)) {
            project.setStatus("failed");
        }
        updateById(project);
    }

    private void initWorkspace(Long projectId) {
        try {
            Files.createDirectories(getProjectWorkspace(projectId));
        } catch (IOException e) {
            log.error("初始化项目工作区失败: projectId={}", projectId, e);
            throw new BusinessException("初始化项目工作区失败: " + e.getMessage(), e);
        }
    }

    private List<String> parseDependencies(List<ProjectFileEntity> files) {
        for (ProjectFileEntity file : files) {
            if ("package.json".equals(file.getPath()) && file.getContent() != null) {
                // 简单提取 dependencies 节点的 key，避免引入完整 JSON 解析依赖
                return extractDependencyKeys(file.getContent());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public ProjectEntity updateProject(Long projectId, UpdateProjectRequest request) {
        ProjectEntity project = getById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }
        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        updateById(project);
        log.info("更新项目成功: id={}", projectId);
        return project;
    }

    @Override
    public void deleteProject(Long projectId) {
        // 1. 删除所有文件记录 + 磁盘文件
        List<ProjectFileEntity> files = projectFileMapper.selectList(
                new LambdaQueryWrapper<ProjectFileEntity>()
                        .eq(ProjectFileEntity::getProjectId, projectId));
        Path workspaceDir = workspaceRoot.resolve(String.valueOf(projectId));
        for (ProjectFileEntity file : files) {
            try {
                Path filePath = workspaceDir.resolve(file.getPath()).normalize();
                if (filePath.startsWith(workspaceDir)) {
                    Files.deleteIfExists(filePath);
                }
            } catch (IOException e) {
                log.warn("删除磁盘文件失败: projectId={}, path={}", projectId, file.getPath());
            }
        }
        projectFileMapper.delete(new LambdaQueryWrapper<ProjectFileEntity>()
                .eq(ProjectFileEntity::getProjectId, projectId));

        // 2. 删除聊天消息
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getProjectId, projectId));

        // 3. 删除任务记录
        generationTaskMapper.delete(new LambdaQueryWrapper<GenerationTaskEntity>()
                .eq(GenerationTaskEntity::getProjectId, projectId));

        // 4. 删除工作区目录
        try {
            if (Files.exists(workspaceDir)) {
                try (var walk = Files.walk(workspaceDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.warn("清理工作区目录失败: projectId={}", projectId);
        }

        // 5. 删除项目实体
        removeById(projectId);
        log.info("级联删除项目成功: id={}", projectId);
    }

    private List<String> extractDependencyKeys(String packageJson) {
        List<String> keys = new ArrayList<>();
        boolean inDeps = false;
        for (String line : packageJson.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("\"dependencies\"")) {
                inDeps = true;
                continue;
            }
            if (inDeps) {
                if (trimmed.startsWith("}")) {
                    break;
                }
                int quoteEnd = trimmed.indexOf("\"", 1);
                if (trimmed.startsWith("\"") && quoteEnd > 0) {
                    keys.add(trimmed.substring(1, quoteEnd));
                }
            }
        }
        return keys;
    }
}
