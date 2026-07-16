package com.lingmaforge.backend.workbench.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * 项目模板初始化服务。
 *
 * <p>用于把后端内置的确定性前端工程模板写入项目，避免让大模型反复生成基础脚手架文件。</p>
 */
@Service
public class ProjectTemplateService {

    private static final String VUE_VITE_TS = "vue-vite-ts";
    private static final String TEMPLATE_ROOT = "project-templates/" + VUE_VITE_TS + "/";
    private static final String TEMPLATE_PATTERN = "classpath*:" + TEMPLATE_ROOT + "**/*";

    private final ProjectFileService projectFileService;
    private final PathMatchingResourcePatternResolver resourceResolver;

    @Autowired
    public ProjectTemplateService(ProjectFileService projectFileService) {
        this(projectFileService, new PathMatchingResourcePatternResolver());
    }

    ProjectTemplateService(ProjectFileService projectFileService,
            PathMatchingResourcePatternResolver resourceResolver) {
        this.projectFileService = projectFileService;
        this.resourceResolver = resourceResolver;
    }

    /**
     * 初始化指定项目模板。
     *
     * <p>模板文件统一通过 {@link ProjectFileService} 写入，确保工作区磁盘文件和数据库文件记录保持一致。</p>
     *
     * @param projectId 项目 ID
     * @param templateName 模板名称
     * @return 已初始化的模板文件数量
     */
    public int initialize(Long projectId, String templateName) {
        if (!VUE_VITE_TS.equals(templateName)) {
            return 0;
        }

        try {
            int count = 0;
            for (Resource resource : resourceResolver.getResources(TEMPLATE_PATTERN)) {
                if (!resource.isReadable() || isDirectory(resource)) {
                    continue;
                }
                String path = templateRelativePath(resource);
                if (!isTextTemplateFile(path)) {
                    continue;
                }
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                projectFileService.writeFile(projectId, path, content, "unchanged");
                count++;
            }
            return count;
        } catch (IOException e) {
            throw new IllegalStateException("初始化项目模板失败: " + templateName, e);
        }
    }

    private boolean isDirectory(Resource resource) throws IOException {
        return resource.getURL().toString().endsWith("/");
    }

    private String templateRelativePath(Resource resource) throws IOException {
        String url = resource.getURL().toString().replace('\\', '/');
        int index = url.indexOf(TEMPLATE_ROOT);
        if (index < 0) {
            throw new IllegalStateException("模板资源不在模板根目录内: " + url);
        }
        String path = url.substring(index + TEMPLATE_ROOT.length());
        if (path.isBlank() || path.contains("..")) {
            throw new IllegalStateException("非法模板路径: " + path);
        }
        return path;
    }

    private boolean isTextTemplateFile(String path) {
        return path.endsWith(".json")
                || path.endsWith(".ts")
                || path.endsWith(".vue")
                || path.endsWith(".html")
                || path.endsWith(".md")
                || path.endsWith(".yml")
                || path.endsWith(".yaml")
                || path.endsWith(".css")
                || path.endsWith(".txt")
                || path.startsWith(".");
    }
}