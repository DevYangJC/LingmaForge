package com.lingmaforge.backend.workbench.ai.tool;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.common.model.ProjectContext;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import com.lingmaforge.backend.workbench.service.ProjectService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 项目上下文工具集，归属需求分析 / 代码生成 / 样式优化 / 迭代修改 Agent。
 *
 * <p>包含两个 @Tool 方法：
 * <ul>
 *   <li>{@link #readProjectContext()} —— 读取项目框架、文件列表、依赖</li>
 *   <li>{@link #readFileContext(List)} —— 读取已生成文件的内容作为生成上下文</li>
 * </ul>
 */
@Component
public class ProjectContextTools {

    private final ProjectService projectService;
    private final ProjectFileService projectFileService;

    public ProjectContextTools(ProjectService projectService, ProjectFileService projectFileService) {
        this.projectService = projectService;
        this.projectFileService = projectFileService;
    }

    /**
     * 读取项目上下文信息，包括框架类型、已有文件列表、package.json 依赖。
     *
     * @return 项目上下文摘要文本
     */
    @Tool("读取项目上下文信息，包括框架类型、已有文件列表、package.json 依赖。用于迭代修改时了解现有项目结构")
    public String readProjectContext() {
        Long projectId = GenerationContext.get().projectId();
        ProjectContext context = projectService.getProjectContext(projectId);
        return """
                框架: %s
                文件列表:
                %s
                package.json 依赖:
                %s
                """.formatted(
                context.framework(),
                String.join("\n", context.filePaths()),
                String.join(", ", context.dependencies()));
    }

    /**
     * 读取项目中已生成文件的内容，用于提供生成上下文。
     *
     * <p>只把 Agent 指定路径的文件内容返回，避免一次性塞入全部文件导致上下文窗口溢出或噪声干扰。</p>
     *
     * @param paths 文件相对路径列表
     * @return 各文件内容拼接的上下文文本
     */
    @Tool("读取项目中已生成文件的内容，用于提供生成上下文。只读取指定路径的文件，避免上下文窗口溢出")
    public String readFileContext(@P("需要读取的文件相对路径列表") List<String> paths) {
        Long projectId = GenerationContext.get().projectId();
        Map<String, String> contents = projectFileService.readFiles(projectId, paths);

        StringBuilder context = new StringBuilder();
        for (String path : paths) {
            String content = contents.get(path);
            if (content != null) {
                context.append("--- 文件: ").append(path).append(" ---\n");
                context.append(content).append("\n\n");
            } else {
                context.append("--- 文件: ").append(path).append(" (尚未生成) ---\n\n");
            }
        }
        return context.toString();
    }
}
