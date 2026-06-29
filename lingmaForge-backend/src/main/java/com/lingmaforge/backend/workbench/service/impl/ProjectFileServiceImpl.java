package com.lingmaforge.backend.workbench.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.workbench.entity.ProjectFileEntity;
import com.lingmaforge.backend.workbench.mapper.ProjectFileMapper;
import com.lingmaforge.backend.common.model.FileNode;
import com.lingmaforge.backend.common.model.Patch;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import com.lingmaforge.backend.workbench.service.ProjectService;

/**
 * 项目文件服务实现：磁盘 + 数据库双写。
 *
 * <p>磁盘文件用于沙箱构建运行，数据库记录用于前端展示与版本管理，两者内容一致。</p>
 */
@Service
public class ProjectFileServiceImpl implements ProjectFileService {

    private static final Logger log = LoggerFactory.getLogger(ProjectFileServiceImpl.class);

    private final ProjectFileMapper projectFileMapper;
    private final ProjectService projectService;

    public ProjectFileServiceImpl(ProjectFileMapper projectFileMapper, ProjectService projectService) {
        this.projectFileMapper = projectFileMapper;
        this.projectService = projectService;
    }

    @Override
    public int writeFile(Long projectId, String path, String content, String status) {
        // 1. 写入磁盘
        Path fullPath = resolveWorkspacePath(projectId, path);
        try {
            Files.createDirectories(fullPath.getParent());
            Files.writeString(fullPath, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("文件写入磁盘失败: projectId={}, path={}", projectId, path, e);
            throw new BusinessException("文件写入失败: " + path, e);
        }

        // 2. 写入数据库（存在则更新）
        ProjectFileEntity entity = findByPath(projectId, path);
        boolean isNew = entity == null;
        if (isNew) {
            entity = new ProjectFileEntity();
            entity.setProjectId(projectId);
            entity.setPath(path);
            entity.setFileType(inferFileType(path));
        }
        entity.setContent(content);
        entity.setChecksum(sha256(content));
        entity.setStatus(status);
        if (isNew) {
            projectFileMapper.insert(entity);
        } else {
            projectFileMapper.updateById(entity);
        }
        return content == null ? 0 : (int) content.lines().count();
    }

    @Override
    public String readFile(Long projectId, String path) {
        ProjectFileEntity entity = findByPath(projectId, path);
        return entity == null ? null : entity.getContent();
    }

    @Override
    public Map<String, String> readFiles(Long projectId, List<String> paths) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String path : paths) {
            result.put(path, readFile(projectId, path));
        }
        return result;
    }

    @Override
    public List<FileNode> getFileTree(Long projectId) {
        List<ProjectFileEntity> files = projectFileMapper.selectList(
                new LambdaQueryWrapper<ProjectFileEntity>()
                        .eq(ProjectFileEntity::getProjectId, projectId)
                        .orderByAsc(ProjectFileEntity::getPath));
        // 基于路径构建树形结构
        FileNode root = new FileNode("", "", "dir", null, null, new ArrayList<>());
        for (ProjectFileEntity file : files) {
            insertIntoTree(root, file);
        }
        return root.children();
    }

    @Override
    public int patchFile(Long projectId, String path, List<Patch> patches) {
        String content = readFile(projectId, path);
        if (content == null) {
            throw new BusinessException("待补丁的文件不存在: " + path);
        }
        List<String> lines = new ArrayList<>(content.lines().toList());
        int applied = 0;
        for (Patch patch : patches) {
            int idx = patch.line() - 1;
            if (idx < 0 || idx >= lines.size()) {
                continue;
            }
            String current = lines.get(idx);
            if (patch.old() == null || Objects.equals(current.trim(), patch.old().trim())) {
                lines.set(idx, patch.newContent());
                applied++;
            }
        }
        writeFile(projectId, path, String.join("\n", lines), "modified");
        return applied;
    }

    @Override
    public List<String> listFilePaths(Long projectId) {
        List<ProjectFileEntity> files = projectFileMapper.selectList(
                new LambdaQueryWrapper<ProjectFileEntity>()
                        .eq(ProjectFileEntity::getProjectId, projectId)
                        .orderByAsc(ProjectFileEntity::getPath));
        return files.stream().map(ProjectFileEntity::getPath).toList();
    }

    private Path resolveWorkspacePath(Long projectId, String relativePath) {
        Path workspace = projectService.getProjectWorkspace(projectId);
        Path fullPath = workspace.resolve(relativePath).normalize();
        // 防止路径穿越
        if (!fullPath.startsWith(workspace)) {
            throw new BusinessException("非法的文件路径: " + relativePath);
        }
        return fullPath;
    }

    private ProjectFileEntity findByPath(Long projectId, String path) {
        return projectFileMapper.selectOne(
                new LambdaQueryWrapper<ProjectFileEntity>()
                        .eq(ProjectFileEntity::getProjectId, projectId)
                        .eq(ProjectFileEntity::getPath, path));
    }

    private void insertIntoTree(FileNode root, ProjectFileEntity file) {
        String[] segments = file.getPath().split("/");
        FileNode current = root;
        StringBuilder accumulated = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            accumulated.append(i == 0 ? "" : "/").append(segment);
            boolean isLeaf = i == segments.length - 1;
            String nodePath = accumulated.toString();
            FileNode child = findChild(current, segment);
            if (child == null) {
                String type = isLeaf ? "file" : "dir";
                String language = isLeaf ? inferLanguage(segment) : null;
                String status = isLeaf ? file.getStatus() : null;
                child = new FileNode(segment, nodePath, type, language, status, new ArrayList<>());
                current.children().add(child);
            }
            current = child;
        }
    }

    private FileNode findChild(FileNode parent, String name) {
        for (FileNode child : parent.children()) {
            if (child.name().equals(name)) {
                return child;
            }
        }
        return null;
    }

    private String inferFileType(String path) {
        if (path.endsWith(".json") || path.endsWith(".config.ts") || path.endsWith("vite.config.ts")) {
            return "config";
        }
        if (path.endsWith(".css")) {
            return "style";
        }
        if (path.contains("/api/")) {
            return "api";
        }
        if (path.contains("/components/")) {
            return "component";
        }
        if (path.contains("/pages/")) {
            return "page";
        }
        if (path.endsWith("App.tsx") || path.endsWith("main.tsx") || path.endsWith(".html")) {
            return "entry";
        }
        return "other";
    }

    private String inferLanguage(String fileName) {
        if (fileName.endsWith(".tsx")) {
            return "typescript";
        }
        if (fileName.endsWith(".ts")) {
            return "typescript";
        }
        if (fileName.endsWith(".css")) {
            return "css";
        }
        if (fileName.endsWith(".json")) {
            return "json";
        }
        if (fileName.endsWith(".html")) {
            return "html";
        }
        return null;
    }

    private static String sha256(String content) {
        if (content == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
