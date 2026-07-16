package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lingmaforge.backend.common.model.BuildErrorAnalysis;
import com.lingmaforge.backend.common.model.FileChangePlan;
import com.lingmaforge.backend.common.model.FileChangeResult;
import com.lingmaforge.backend.common.model.IterationIntent;
import com.lingmaforge.backend.common.model.ModificationPlan;
import com.lingmaforge.backend.common.model.ProjectContext;
import com.lingmaforge.backend.workbench.ai.node.BuildErrorAnalysisNode;
import com.lingmaforge.backend.workbench.ai.node.CodePatchNode;
import com.lingmaforge.backend.workbench.ai.node.IterationIntentAnalysisNode;
import com.lingmaforge.backend.workbench.ai.node.ModificationPlanningNode;
import com.lingmaforge.backend.workbench.ai.node.ProjectContextLoadNode;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import com.lingmaforge.backend.workbench.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 对话式迭代智能体节点测试。
 *
 * <p>这些节点是 Cursor 类交互的后端最小执行单元：识别本轮修改意图、装载项目上下文、
 * 生成结构化修改计划、应用文件变更，并在构建失败后生成修复分析。</p>
 */
@DisplayName("对话式迭代节点")
@ExtendWith(MockitoExtension.class)
class IterationNodesTest {

    private static final String TASK_ID = "iteration-task-001";
    private static final String PROJECT_ID = "1";
    private static final String PROMPT = "把首页按钮改成绿色，并增加一个登录入口";

    @Mock private IterationAgent iterationAgent;
    @Mock private ProjectService projectService;
    @Mock private ProjectFileService projectFileService;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private GenerationStreamEmitter streamEmitter;
    @Mock private com.lingmaforge.backend.workbench.ai.service.IterationEditor iterationEditor;
    @Mock private com.lingmaforge.backend.workbench.ai.plan.PlanTracker planTracker;
    @Mock private com.lingmaforge.backend.workbench.ai.stream.StreamingBridge streamingBridge;
    @Mock private com.lingmaforge.backend.workbench.ai.memory.ContextCompactionService compactionService;
    @Mock private java.util.function.Function<String, dev.langchain4j.memory.chat.MessageWindowChatMemory> chatMemoryProvider;

    @BeforeEach
    void setUp() {
        when(streamRegistry.get(TASK_ID)).thenReturn(streamEmitter);
    }

    @AfterEach
    void tearDown() {
        GenerationContext.clear();
    }

    @Test
    @DisplayName("意图分析节点写入结构化意图")
    void intentAnalysisNodeShouldWriteIterationIntent() {
        IterationIntent intent = new IterationIntent(
                "feature",
                "调整首页按钮并增加登录入口",
                List.of("src/App.vue"),
                true);
        when(iterationAgent.analyzeIntent(PROMPT, "")).thenReturn(intent);

        IterationIntentAnalysisNode node = new IterationIntentAnalysisNode(iterationAgent, streamRegistry);
        Map<String, Object> result = node.execute(baseState(null));

        assertThat(result).containsEntry(CodeGenState.ITERATION_INTENT, intent);
        verify(iterationAgent).analyzeIntent(PROMPT, "");
    }

    @Test
    @DisplayName("项目上下文节点读取候选文件并写入上下文摘要")
    void projectContextLoadNodeShouldWriteIterationContext() {
        ProjectContext context = new ProjectContext(
                "vue-vite-ts",
                List.of("src/App.vue", "src/main.ts"),
                List.of("vue", "vite"));
        IterationIntent intent = new IterationIntent(
                "feature",
                "调整首页",
                List.of("src/App.vue"),
                true);
        when(projectService.getProjectContext(1L)).thenReturn(context);
        when(projectFileService.readFiles(eq(1L), anyList()))
                .thenReturn(Map.of("src/App.vue", "<template><button>Go</button></template>"));

        ProjectContextLoadNode node = new ProjectContextLoadNode(projectService, projectFileService, streamRegistry);
        Map<String, Object> result = node.execute(baseState(Map.of(CodeGenState.ITERATION_INTENT, intent)));

        assertThat(result).containsKey(CodeGenState.ITERATION_CONTEXT);
        String summary = (String) result.get(CodeGenState.ITERATION_CONTEXT);
        assertThat(summary)
                .contains("vue-vite-ts")
                .contains("src/App.vue")
                .contains("<template><button>Go</button></template>");
    }


    @Test
    @DisplayName("项目上下文节点在 Vue 项目中始终加载模板关键文件")
    void projectContextLoadNodeShouldIncludeVueTemplateAnchorFiles() {
        ProjectContext context = new ProjectContext(
                "vue-vite-ts",
                List.of(
                        "package.json",
                        "src/main.ts",
                        "src/App.vue",
                        "src/router/index.ts",
                        "src/views/Home.vue",
                        "src/components/PrimaryButton.vue"),
                List.of("vue", "vue-router", "pinia"));
        IterationIntent intent = new IterationIntent(
                "feature",
                "调整首页",
                List.of("src/views/Home.vue"),
                true);
        when(projectService.getProjectContext(1L)).thenReturn(context);
        when(projectFileService.readFiles(eq(1L), anyList()))
                .thenReturn(Map.of(
                        "package.json", "{\"dependencies\":{\"vue\":\"^3.5.0\"}}",
                        "src/main.ts", "createApp(App).mount('#app')",
                        "src/App.vue", "<template><RouterView /></template>",
                        "src/router/index.ts", "export default []",
                        "src/views/Home.vue", "<template>Home</template>"));

        ProjectContextLoadNode node = new ProjectContextLoadNode(projectService, projectFileService, streamRegistry);
        node.execute(baseState(Map.of(CodeGenState.ITERATION_INTENT, intent)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> pathsCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectFileService).readFiles(eq(1L), pathsCaptor.capture());
        assertThat(pathsCaptor.getValue())
                .contains(
                        "package.json",
                        "src/main.ts",
                        "src/App.vue",
                        "src/router/index.ts",
                        "src/views/Home.vue");
    }
    @Test
    @DisplayName("修改规划节点写入结构化修改计划")
    void modificationPlanningNodeShouldWriteModificationPlan() {
        IterationIntent intent = new IterationIntent("feature", "调整首页", List.of("src/App.vue"), true);
        ModificationPlan plan = new ModificationPlan(
                "更新首页入口",
                List.of(new FileChangePlan("src/App.vue", "update", "增加登录入口", "<template>Login</template>")),
                List.of("需要构建验证"));
        when(iterationAgent.planModification(PROMPT, "project context", intent, null)).thenReturn(plan);

        ModificationPlanningNode node = new ModificationPlanningNode(iterationAgent, streamRegistry);
        Map<String, Object> result = node.execute(baseState(Map.of(
                CodeGenState.ITERATION_CONTEXT, "project context",
                CodeGenState.ITERATION_INTENT, intent)));

        assertThat(result).containsEntry(CodeGenState.MODIFICATION_PLAN, plan);
        verify(iterationAgent).planModification(PROMPT, "project context", intent, null);
    }


    @Test
    @DisplayName("修改规划节点在构建失败重试时带上错误分析结果")
    void modificationPlanningNodeShouldUseBuildErrorAnalysisOnRetry() {
        IterationIntent intent = new IterationIntent("fix", "修复构建错误", List.of("src/App.vue"), true);
        BuildErrorAnalysis analysis = new BuildErrorAnalysis(
                "type",
                "组件属性类型不匹配",
                List.of("src/App.vue"),
                "修正按钮组件 props 类型");
        ModificationPlan plan = new ModificationPlan(
                "定向修复构建错误",
                List.of(new FileChangePlan("src/App.vue", "update", "修复类型错误", "<template>Fixed</template>")),
                List.of());
        when(iterationAgent.planModification(PROMPT, "project context", intent, analysis)).thenReturn(plan);

        ModificationPlanningNode node = new ModificationPlanningNode(iterationAgent, streamRegistry);
        Map<String, Object> result = node.execute(baseState(Map.of(
                CodeGenState.ITERATION_CONTEXT, "project context",
                CodeGenState.ITERATION_INTENT, intent,
                CodeGenState.BUILD_ERROR_ANALYSIS, analysis)));

        assertThat(result).containsEntry(CodeGenState.MODIFICATION_PLAN, plan);
        verify(iterationAgent).planModification(PROMPT, "project context", intent, analysis);
    }
    @Test
    @DisplayName("补丁节点将 create/update/delete 映射为文件服务调用")
    void codePatchNodeShouldApplyFileChanges() {
        ModificationPlan plan = new ModificationPlan(
                "批量修改文件",
                List.of(
                        new FileChangePlan("src/App.vue", "update", "更新按钮", "<template>Updated</template>"),
                        new FileChangePlan("src/Login.vue", "create", "新增登录入口", "<template>Login</template>"),
                        new FileChangePlan("src/Old.vue", "delete", "删除旧入口", "")),
                List.of());
        when(projectFileService.writeFile(1L, "src/App.vue", "<template>Updated</template>", "modified"))
                .thenReturn(1);
        when(projectFileService.writeFile(1L, "src/Login.vue", "<template>Login</template>", "new"))
                .thenReturn(1);

        CodePatchNode node = new CodePatchNode(iterationEditor, streamRegistry, planTracker, projectService, projectFileService, streamingBridge, compactionService, chatMemoryProvider);
        Map<String, Object> result = node.execute(baseState(Map.of(CodeGenState.MODIFICATION_PLAN, plan)));

        assertThat(result).containsKey(CodeGenState.MODIFIED_FILES);
        @SuppressWarnings("unchecked")
        List<FileChangeResult> results = (List<FileChangeResult>) result.get(CodeGenState.MODIFIED_FILES);
        assertThat(results)
                .extracting(FileChangeResult::path)
                .containsExactly("src/App.vue", "src/Login.vue", "src/Old.vue");
        assertThat(results).allMatch(FileChangeResult::success);
        verify(projectFileService).writeFile(1L, "src/App.vue", "<template>Updated</template>", "modified");
        verify(projectFileService).writeFile(1L, "src/Login.vue", "<template>Login</template>", "new");
        verify(projectFileService).deleteFile(1L, "src/Old.vue");
    }


    @Test
    @DisplayName("补丁节点默认拒绝修改受保护的 Vue 模板基础文件")
    void codePatchNodeShouldRejectProtectedTemplateFiles() {
        ModificationPlan plan = new ModificationPlan(
                "修改首页并误改基础依赖",
                List.of(
                        new FileChangePlan("package.json", "update", "不要直接改依赖", "{}"),
                        new FileChangePlan("src/views/Home.vue", "update", "更新首页", "<template>Home</template>")),
                List.of());
        when(projectFileService.writeFile(1L, "src/views/Home.vue", "<template>Home</template>", "modified"))
                .thenReturn(1);

        CodePatchNode node = new CodePatchNode(iterationEditor, streamRegistry, planTracker, projectService, projectFileService, streamingBridge, compactionService, chatMemoryProvider);
        Map<String, Object> result = node.execute(baseState(Map.of(CodeGenState.MODIFICATION_PLAN, plan)));

        @SuppressWarnings("unchecked")
        List<FileChangeResult> results = (List<FileChangeResult>) result.get(CodeGenState.MODIFIED_FILES);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).message()).contains("受保护模板文件");
        assertThat(results.get(1).success()).isTrue();
        verify(projectFileService, never()).writeFile(eq(1L), eq("package.json"), anyString(), anyString());
        verify(projectFileService).writeFile(1L, "src/views/Home.vue", "<template>Home</template>", "modified");
    }
    @Test
    @DisplayName("构建错误分析节点写入结构化错误分析")
    void buildErrorAnalysisNodeShouldWriteAnalysis() {
        ModificationPlan plan = new ModificationPlan("更新首页", List.of(), List.of());
        BuildErrorAnalysis analysis = new BuildErrorAnalysis(
                "type",
                "组件属性类型不匹配",
                List.of("src/App.vue"),
                "修正按钮组件 props 类型");
        when(iterationAgent.analyzeBuildError("TS2322", plan)).thenReturn(analysis);

        BuildErrorAnalysisNode node = new BuildErrorAnalysisNode(iterationAgent, streamRegistry);
        Map<String, Object> result = node.execute(baseState(Map.of(
                CodeGenState.BUILD_ERROR, "TS2322",
                CodeGenState.MODIFICATION_PLAN, plan)));

        assertThat(result).containsEntry(CodeGenState.BUILD_ERROR_ANALYSIS, analysis);
        verify(iterationAgent).analyzeBuildError("TS2322", plan);
    }

    private CodeGenState baseState(Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>();
        data.put(CodeGenState.PROMPT, PROMPT);
        data.put(CodeGenState.ITERATION_PROMPT, PROMPT);
        data.put(CodeGenState.PROJECT_ID, PROJECT_ID);
        data.put(CodeGenState.TASK_ID, TASK_ID);
        if (extra != null) {
            data.putAll(extra);
        }
        return new CodeGenState(data);
    }
}
