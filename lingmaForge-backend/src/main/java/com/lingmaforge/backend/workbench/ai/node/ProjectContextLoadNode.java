package com.lingmaforge.backend.workbench.ai.node;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.lingmaforge.backend.common.model.IterationIntent;
import com.lingmaforge.backend.common.model.ProjectContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.support.TemplateFilePolicy;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import com.lingmaforge.backend.workbench.service.ProjectService;

/**
 * 对话式迭代的项目上下文装载节点。
 *
 * <p>根据项目元信息和意图分析结果，收集本轮修改最相关的文件内容，拼装成给规划
 * Agent 使用的紧凑上下文文本。该节点只读取项目，不产生文件变更。</p>
 */
@Component
public class ProjectContextLoadNode extends AbstractCodeGenNode {

    /** 节点名称，对应 LangGraph4j 状态图和 SSE 事件。 */
    public static final String NODE_NAME = "project_context_load";

    private static final int FALLBACK_FILE_LIMIT = 8;

    private static final String VUE_FRAMEWORK = "vue-vite-ts";

private final ProjectService projectService;
    private final ProjectFileService projectFileService;

    public ProjectContextLoadNode(
            ProjectService projectService,
            ProjectFileService projectFileService,
            GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.projectService = projectService;
        this.projectFileService = projectFileService;
    }

    /**
     * 读取项目上下文和候选文件内容。
     *
     * @param state 当前代码生成状态
     * @return 状态更新，写入 iterationContext
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在读取项目上下文...");
        try {
            Long projectId = projectId(state);
            ProjectContext context = projectService.getProjectContext(projectId);
            List<String> selectedPaths = selectFilePaths(state.iterationIntent().orElse(null), context);
            Map<String, String> fileContents = selectedPaths.isEmpty()
                    ? Map.of()
                    : projectFileService.readFiles(projectId, selectedPaths);
            String summary = buildContextSummary(context, selectedPaths, fileContents);
            if (emitter != null) {
                emitter.emitNode(NODE_NAME, "已加载 " + selectedPaths.size() + " 个候选文件", "TEXT");
            }
            return Map.of(CodeGenState.ITERATION_CONTEXT, summary);
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }

    private List<String> selectFilePaths(IterationIntent intent, ProjectContext context) {
        Set<String> paths = new LinkedHashSet<>();
        if (intent != null && intent.targetFiles() != null) {
            paths.addAll(intent.targetFiles());
        }
        if (isVueProject(context)) {
            addExistingTemplateFiles(paths, context);
        }
        if (paths.isEmpty() && context.filePaths() != null) {
            context.filePaths().stream()
                    .limit(FALLBACK_FILE_LIMIT)
                    .forEach(paths::add);
        }
        return List.copyOf(paths);
    }

    /**
     * Vue 项目在迭代规划时需要始终带上模板入口文件，避免智能体脱离脚手架边界生成 React 或裸 HTML 代码。
     */
    private void addExistingTemplateFiles(Set<String> paths, ProjectContext context) {
        if (context.filePaths() == null) {
            return;
        }
        Set<String> existingPaths = new LinkedHashSet<>(context.filePaths());
        TemplateFilePolicy.vueContextAnchorFiles().stream()
                .filter(existingPaths::contains)
                .forEach(paths::add);
    }

    private boolean isVueProject(ProjectContext context) {
        return context != null && VUE_FRAMEWORK.equals(context.framework());
    }

    private String buildContextSummary(
            ProjectContext context,
            List<String> selectedPaths,
            Map<String, String> fileContents) {
        StringBuilder builder = new StringBuilder();
        builder.append("framework: ").append(nullToEmpty(context.framework())).append('\n');
        builder.append("dependencies:\n");
        if (context.dependencies() != null) {
            context.dependencies().forEach(dep -> builder.append("- ").append(dep).append('\n'));
        }
        builder.append("projectFiles:\n");
        if (context.filePaths() != null) {
            context.filePaths().forEach(path -> builder.append("- ").append(path).append('\n'));
        }
        builder.append("selectedFiles:\n");
        for (String path : selectedPaths) {
            builder.append("## ").append(path).append('\n');
            builder.append(nullToEmpty(fileContents.get(path))).append('\n');
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
