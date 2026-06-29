package com.lingmaforge.backend.workbench.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lingmaforge.backend.common.api.Result;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.ResultCode;
import com.lingmaforge.backend.common.model.CreateProjectRequest;
import com.lingmaforge.backend.common.model.FileNode;
import com.lingmaforge.backend.common.model.ProjectResponse;
import com.lingmaforge.backend.common.model.UpdateFileRequest;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import com.lingmaforge.backend.workbench.service.ProjectService;

/**
 * 项目管理相关的 REST 接口。
 *
 * <p>提供项目 CRUD、文件树查询、文件读写等能力，统一返回 {@link Result}。</p>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectFileService projectFileService;

    public ProjectController(ProjectService projectService, ProjectFileService projectFileService) {
        this.projectService = projectService;
        this.projectFileService = projectFileService;
    }

    /**
     * 查询项目列表。
     *
     * @return 项目列表
     */
    @GetMapping
    public Result<List<ProjectResponse>> listProjects() {
        List<ProjectResponse> projects = projectService.list()
                .stream()
                .map(ProjectResponse::from)
                .toList();
        return Result.ok(projects);
    }

    /**
     * 创建项目。
     *
     * @param request 项目创建请求
     * @return 创建后的项目信息
     */
    @PostMapping
    public Result<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return Result.ok(ProjectResponse.from(projectService.createProject(request)));
    }

    /**
     * 查询项目文件树。
     *
     * @param id 项目 ID
     * @return 文件树根节点列表
     */
    @GetMapping("/{id}/tree")
    public Result<List<FileNode>> getFileTree(@PathVariable Long id) {
        ensureProjectExists(id);
        return Result.ok(projectFileService.getFileTree(id));
    }

    /**
     * 读取项目文件内容。
     *
     * @param id   项目 ID
     * @param path 文件相对路径
     * @return 文件内容
     */
    @GetMapping("/{id}/file")
    public Result<String> readFile(@PathVariable Long id, @RequestParam String path) {
        ensureProjectExists(id);
        String content = projectFileService.readFile(id, path);
        if (content == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        return Result.ok(content);
    }

    /**
     * 更新项目文件内容。
     *
     * @param id      项目 ID
     * @param request 文件更新请求
     * @return 操作结果
     */
    @PutMapping("/{id}/file")
    public Result<Void> updateFile(@PathVariable Long id, @Valid @RequestBody UpdateFileRequest request) {
        ensureProjectExists(id);
        projectFileService.writeFile(id, request.path(), request.content(), "modified");
        return Result.ok(null);
    }

    private void ensureProjectExists(Long id) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }
    }
}
