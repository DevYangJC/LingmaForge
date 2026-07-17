package com.lingmaforge.backend.workbench.ai.node;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.common.model.ProjectContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.plan.PlanTracker;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.IterationEditor;
import com.lingmaforge.backend.workbench.ai.memory.ContextCompactionService;
import com.lingmaforge.backend.workbench.ai.stream.StreamingBridge;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import com.lingmaforge.backend.workbench.ai.memory.ContextCompactionService;

import com.lingmaforge.backend.workbench.ai.stream.StreamingContext;
import com.lingmaforge.backend.workbench.ai.support.TemplateFilePolicy;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import com.lingmaforge.backend.workbench.service.ProjectService;

/**
 * 对话式迭代编辑节点——已收敛为单一 agentic 节点，由 {@link StreamingBridge} 提供流式基础底座。
 */
@Component
public class CodePatchNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(CodePatchNode.class);

    public static final String NODE_NAME = "code_patch";

    private static final int FALLBACK_FILE_LIMIT = 8;
    private static final String VUE_FRAMEWORK = "vue-vite-ts";

    private final IterationEditor editor;
    private final PlanTracker planTracker;
    private final ProjectService projectService;
    private final ProjectFileService projectFileService;
    private final StreamingBridge streamingBridge;
    private final ContextCompactionService compactionService;
    private final java.util.function.Function<String, MessageWindowChatMemory> chatMemoryProvider;

    @Autowired
    public CodePatchNode(AgentFactory agentFactory,
            GenerationStreamRegistry streamRegistry,
            PlanTracker planTracker,
            ProjectService projectService,
            ProjectFileService projectFileService,
            StreamingBridge streamingBridge,
            ContextCompactionService compactionService,
            java.util.function.Function<String, MessageWindowChatMemory> chatMemoryProvider) {
        this(agentFactory.createIterationEditor(),
                streamRegistry,
                planTracker,
                projectService,
                projectFileService,
                streamingBridge,
                compactionService,
                chatMemoryProvider);
    }

    public CodePatchNode(IterationEditor editor,
            GenerationStreamRegistry streamRegistry,
            PlanTracker planTracker,
            ProjectService projectService,
            ProjectFileService projectFileService,
            StreamingBridge streamingBridge,
            ContextCompactionService compactionService,
            java.util.function.Function<String, MessageWindowChatMemory> chatMemoryProvider) {
        super(streamRegistry);
        this.editor = editor;
        this.planTracker = planTracker;
        this.projectService = projectService;
        this.projectFileService = projectFileService;
        this.streamingBridge = streamingBridge;
        this.compactionService = compactionService;
        this.chatMemoryProvider = chatMemoryProvider;
    }

    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在调用 AI 修改代码...");
        Long projectId = projectId(state);
        String taskId = state.taskId().orElse("");

        try {
            String userPrompt = state.iterationPrompt().orElse("");
            String buildError = state.buildError().orElse(null);
            String projectContext = buildProjectContext(projectId);
            String fullPrompt = buildEditPrompt(userPrompt, projectContext, buildError);

            log.info("[{}] agentic 迭代编辑开始：projectId={}", taskId, projectId);

            StreamingContext ctx = StreamingContext.builder()
                    .emitter(emitter)
                    .nodeName(NODE_NAME)
                    .taskId(taskId)
                    .stopRegistry(getStreamRegistry())
                    .onToken(t -> emitter.emitNode(NODE_NAME, t, "TEXT"))
                    .build();

            String memoryKey = projectId + "_vue-project";
            MessageWindowChatMemory memory = chatMemoryProvider.apply(memoryKey);
            compactionService.autoCompactIfNeeded(memory, memoryKey);
            streamingBridge.bridge(editor.edit(projectId, fullPrompt), ctx);

            planTracker.clear(PlanTracker.key(projectId, taskId));
            return Map.of();
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }

    private String buildProjectContext(Long projectId) {
        ProjectContext context = projectService.getProjectContext(projectId);
        List<String> selectedPaths = selectFilePaths(context);
        Map<String, String> fileContents = selectedPaths.isEmpty()
                ? Map.of()
                : projectFileService.readFiles(projectId, selectedPaths);

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
        builder.append("selectedFileContents:\n");
        for (String path : selectedPaths) {
            builder.append("## ").append(path).append('\n');
            builder.append(nullToEmpty(fileContents.get(path))).append('\n');
        }
        return builder.toString();
    }

    private List<String> selectFilePaths(ProjectContext context) {
        Set<String> paths = new LinkedHashSet<>();
        if (isVueProject(context)) {
            addExistingTemplateFiles(paths, context);
        }
        if (paths.isEmpty() && context.filePaths() != null) {
            context.filePaths().stream().limit(FALLBACK_FILE_LIMIT).forEach(paths::add);
        }
        return List.copyOf(paths);
    }

    private void addExistingTemplateFiles(Set<String> paths, ProjectContext context) {
        if (context.filePaths() == null) return;
        Set<String> existingPaths = new LinkedHashSet<>(context.filePaths());
        TemplateFilePolicy.vueContextAnchorFiles().stream()
                .filter(existingPaths::contains)
                .forEach(paths::add);
    }

    private boolean isVueProject(ProjectContext context) {
        return context != null && VUE_FRAMEWORK.equals(context.framework());
    }

    private String buildEditPrompt(String userPrompt, String projectContext, String buildError) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个前端项目迭代修改助手，根据用户指令修改项目文件。\n\n");
        sb.append("## 用户修改指令\n").append(userPrompt).append("\n\n");
        sb.append("## 当前项目上下文\n").append(projectContext).append("\n\n");
        if (buildError != null && !buildError.isBlank()) {
            sb.append("## 上次构建失败错误（必须优先修复！）\n").append(buildError).append("\n\n");
        }
        sb.append("""
                ## 工作流程
                1. 用 readFileContext 读取需要修改的文件当前内容
                2. 逐项修改：对每个文件用 writeFile（完整覆盖）或 patchFile（增量修改）
                3. 每完成一项用 updatePlan 更新进度
                4. 全部完成后调用 exit

                ## 注意事项
                - 通过 readFileContext 确认文件现状后再修改，避免越改越乱
                - 用 writeFile 的完整覆盖能力代替复杂的 patch
                - 不要修改 package.json / vite.config.ts / tsconfig.json 等模板脚手架文件
                """);
        return sb.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
