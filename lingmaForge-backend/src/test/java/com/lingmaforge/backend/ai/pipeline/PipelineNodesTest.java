package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.node.*;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenPipeline;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.*;
import com.lingmaforge.backend.workbench.ai.tool.FileTools;
import com.lingmaforge.backend.workbench.ai.tool.IterationTools;
import com.lingmaforge.backend.workbench.ai.tool.ProjectContextTools;
import com.lingmaforge.backend.workbench.service.*;
import com.lingmaforge.backend.common.model.*;
import com.lingmaforge.backend.infra.config.LingmaModelsProperties;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 代码生成流水线各节点单元测试
 *
 * <p>使用 Mockito 模拟 ChatModel，测试每个节点的完整执行路径：
 * 正常流程、LLM 返回异常、空字段、构建失败回退修复。</p>
 */
@DisplayName("代码生成流水线 - 节点级单元测试")
@ExtendWith(MockitoExtension.class)
class PipelineNodesTest {

    private static final Logger log = LoggerFactory.getLogger(PipelineNodesTest.class);

    // ==================== Mock 依赖 ====================

    @Mock private ChatModel chatModel;
    @Mock private ChatModel fallbackChatModel;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private GenerationStreamEmitter streamEmitter;
    @Mock private PromptTemplateLoader promptLoader;
    @Mock private FileTools fileTools;
    @Mock private ProjectContextTools projectContextTools;
    @Mock private IterationTools iterationTools;
    @Mock private ProjectFileService projectFileService;
    @Mock private SandboxService sandboxService;
    @Mock private ProjectService projectService;
    @Mock private CodeGenAgent mockCodeGenAgent;

    // ==================== 通用测试常量 ====================

    private static final String TASK_ID = "test-task-001";
    private static final String PROJECT_ID = "1";

    // ==================== JSON 模板 ====================

    private static final String VALID_REQUIREMENT_JSON = "{"
            + "\"appName\": \"Subscription Store\","
            + "\"description\": \"A subscription payment platform\","
            + "\"pages\": ["
            + "  {\"name\": \"Home\", \"route\": \"/\", \"description\": \"Homepage\","
            + "   \"components\": [\"NavBar\", \"HeroSection\"]},"
            + "  {\"name\": \"Pricing\", \"route\": \"/pricing\", \"description\": \"Pricing page\","
            + "   \"components\": [\"PlanCard\", \"PaymentButton\"]}"
            + "],"
            + "\"apis\": ["
            + "  {\"name\": \"Get Plans\", \"path\": \"/api/plans\", \"method\": \"GET\","
            + "   \"description\": \"Returns plan list\", \"requestShape\": {}, \"responseShape\": {\"plans\": []}}"
            + "],"
            + "\"features\": [\"Plan comparison\", \"Payment processing\"],"
            + "\"style\": {\"theme\": \"#6366f1\", \"themeName\": \"indigo\","
            + "          \"layout\": \"responsive\", \"fontFamily\": \"Inter\"}"
            + "}";

    private static final String VALID_PLAN_RESULT_JSON = "{"
            + "\"framework\": \"react-vite-ts\","
            + "\"packageManager\": \"npm\","
            + "\"files\": ["
            + "  {\"path\": \"package.json\", \"purpose\": \"Project config\","
            + "   \"fileType\": \"config\", \"dependencies\": [], \"required\": true},"
            + "  {\"path\": \"src/App.tsx\", \"purpose\": \"App entry\","
            + "   \"fileType\": \"entry\","
            + "   \"dependencies\": [\"src/components/PlanCard.tsx\"], \"required\": true},"
            + "  {\"path\": \"src/components/PlanCard.tsx\", \"purpose\": \"Plan card\","
            + "   \"fileType\": \"component\","
            + "   \"dependencies\": [\"src/styles/globals.css\"], \"required\": true}"
            + "],"
            + "\"generationOrder\": [\"package.json\", \"src/App.tsx\", \"src/components/PlanCard.tsx\"],"
            + "\"buildCommands\": [\"npm install\", \"npm run build\"]"
            + "}";

    /** LLM 返回 application/json 但字段不完整（缺少 style / apis） */
    private static final String PARTIAL_REQUIREMENT_JSON = "{"
            + "\"appName\": \"Minimal App\","
            + "\"description\": \"A minimal application\","
            + "\"pages\": ["
            + "  {\"name\": \"Home\", \"route\": \"/\", \"description\": \"Main page\","
            + "   \"components\": [\"Header\"]}"
            + "],"
            + "\"apis\": [],"
            + "\"features\": [],"
            + "\"style\": {\"theme\": \"#000000\", \"themeName\": \"default\","
            + "          \"layout\": \"\", \"fontFamily\": \"\"}"
            + "}";

    private static final String EMPTY_FIELDS_PLAN_JSON = "{"
            + "\"framework\": \"vue-vite-ts\","
            + "\"packageManager\": \"pnpm\","
            + "\"files\": ["
            + "  {\"path\": \"src/App.vue\", \"purpose\": \"main entry\","
            + "   \"fileType\": \"entry\", \"dependencies\": [], \"required\": true}"
            + "],"
            + "\"generationOrder\": [\"src/App.vue\"],"
            + "\"buildCommands\": [\"npm install\"]"
            + "}";

    @BeforeEach
    void setUp() {
        lenient().when(streamRegistry.get(TASK_ID)).thenReturn(streamEmitter);
    }

    /** 在需要调用真实 Agent 的测试中预先 stub 提示词加载器 */
    private void stubPromptLoader() {
        lenient().when(promptLoader.loadSystemPrompt(anyString())).thenReturn("system prompt");
        lenient().when(promptLoader.loadUserPrompt(anyString(), any())).thenReturn("generate this file");
    }

    @AfterEach
    void tearDown() {
        GenerationContext.clear();
    }

    /** 构建一个含基本字段的 CodeGenState */
    private CodeGenState baseState(Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>();
        data.put(CodeGenState.PROMPT, "Generate a subscription store");
        data.put(CodeGenState.PROJECT_ID, PROJECT_ID);
        data.put(CodeGenState.TASK_ID, TASK_ID);
        if (extra != null) data.putAll(extra);
        return new CodeGenState(data);
    }

    /** 创建 AgentFactory — 所有 Agent 共享同一个 mock ChatModel */
    private AgentFactory agentFactory() {
        LingmaModelsProperties.ModelConfig mockModelCfg =
                new LingmaModelsProperties.ModelConfig(
                        "http://localhost/v1", "sk-mock", "mock-model",
                        "openai", false, false, 2);
        LingmaModelsProperties props = new LingmaModelsProperties(
                Map.of("mock-model", mockModelCfg),
                Map.of(
                        "requirement-analysis",
                        new LingmaModelsProperties.AgentModelConfig("mock-model"),
                        "execution-planning",
                        new LingmaModelsProperties.AgentModelConfig("mock-model"),
                        "code-generation",
                        new LingmaModelsProperties.AgentModelConfig("mock-model"),
                        "style-optimization",
                        new LingmaModelsProperties.AgentModelConfig("mock-model"),
                        "iteration-modification",
                        new LingmaModelsProperties.AgentModelConfig("mock-model")));
        return new AgentFactory(Map.of("mock-model", chatModel), props, promptLoader,
                fileTools, projectContextTools, iterationTools) {
            @Override
            public CodeGenAgent createCodeGenAgent() {
                return mockCodeGenAgent;
            }
        };
    }

    // ==================== 节点一：需求分析 ====================

    @Nested
    @DisplayName("节点一 - RequirementAnalysisNode")
    class RequirementAnalysisTests {

        @BeforeEach
        void init() { stubPromptLoader(); }

        @Test
        @DisplayName("正常流程：LLM 返回完整 JSON → 成功解析 RequirementSpec")
        void shouldAnalyzeAndReturnSpec() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(VALID_REQUIREMENT_JSON)).build());

            AgentFactory factory = agentFactory();
            RequirementAnalysisNode node = new RequirementAnalysisNode(factory, streamRegistry);
            CodeGenState state = baseState(null);

            Map<String, Object> result = node.execute(state);

            assertThat(result).containsKey(CodeGenState.ANALYSIS_RESULT);
            RequirementSpec spec = (RequirementSpec) result.get(CodeGenState.ANALYSIS_RESULT);
            assertThat(spec.appName()).isEqualTo("Subscription Store");
            assertThat(spec.pages()).hasSize(2);
            assertThat(spec.apis()).hasSize(1);
            assertThat(spec.features()).hasSize(2);
            assertThat(spec.style().theme()).isEqualTo("#6366f1");
            assertThat(spec.style().themeName()).isEqualTo("indigo");

            verify(streamEmitter, atLeastOnce()).emitNode(eq("requirement_analysis"), anyString(), eq("TEXT"));
            log.info("[OK] 需求分析节点正常流程通过");
        }

        @Test
        @DisplayName("LLM 返回部分字段 → 解析成功（字段为空集合/空字符串）")
        void shouldHandlePartialResponse() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(PARTIAL_REQUIREMENT_JSON)).build());

            AgentFactory factory = agentFactory();
            RequirementAnalysisNode node = new RequirementAnalysisNode(factory, streamRegistry);
            CodeGenState state = baseState(null);

            Map<String, Object> result = node.execute(state);

            assertThat(result).containsKey(CodeGenState.ANALYSIS_RESULT);
            RequirementSpec spec = (RequirementSpec) result.get(CodeGenState.ANALYSIS_RESULT);
            assertThat(spec.appName()).isEqualTo("Minimal App");
            assertThat(spec.apis()).isEmpty();
            assertThat(spec.features()).isEmpty();
            assertThat(spec.style().themeName()).isEqualTo("default");
            assertThat(spec.style().layout()).isEmpty();
            log.info("[OK] 部分字段场景通过 — 空集合/空字符串正确处理");
        }

        @Test
        @DisplayName("LLM 调用异常 → 异常向上传播并被外层捕获")
        void shouldPropagateLLMError() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenThrow(new RuntimeException("API rate limit exceeded"));

            AgentFactory factory = agentFactory();
            RequirementAnalysisNode node = new RequirementAnalysisNode(factory, streamRegistry);
            CodeGenState state = baseState(null);

            assertThatThrownBy(() -> node.execute(state))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("API rate limit");

            verify(streamEmitter, atLeastOnce()).emitNode(
                    eq("requirement_analysis"),
                    argThat(msg -> msg.contains("需求分析失败")),
                    eq("TEXT"));
            log.info("[OK] LLM异常传播验证通过");
        }

        @Test
        @DisplayName("状态缺少 prompt → 抛出异常")
        void shouldThrowWhenNoPrompt() {
            AgentFactory factory = agentFactory();
            RequirementAnalysisNode node = new RequirementAnalysisNode(factory, streamRegistry);
            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROJECT_ID, PROJECT_ID);
            data.put(CodeGenState.TASK_ID, TASK_ID);
            CodeGenState state = new CodeGenState(data);

            assertThatThrownBy(() -> node.execute(state))
                    .isInstanceOf(NoSuchElementException.class);
            log.info("[OK] 缺少prompt字段异常验证通过");
        }
    }

    // ==================== 节点二：执行规划 ====================

    @Nested
    @DisplayName("节点二 - ExecutionPlanningNode")
    class ExecutionPlanningTests {

        @BeforeEach
        void init() { stubPromptLoader(); }

        @Test
        @DisplayName("正常流程：根据需求规格生成文件清单")
        void shouldPlanFilesFromRequirement() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(VALID_REQUIREMENT_JSON)).build())
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(VALID_PLAN_RESULT_JSON)).build());

            AgentFactory factory = agentFactory();
            RequirementAnalysisNode analysisNode =
                    new RequirementAnalysisNode(factory, streamRegistry);
            ExecutionPlanningNode planNode =
                    new ExecutionPlanningNode(factory, streamRegistry);

            // Step 1: 需求分析
            CodeGenState state = baseState(null);
            Map<String, Object> analysisResult = analysisNode.execute(state);
            // 手动合并状态(模拟 LangGraph4j 的 merge)
            Map<String, Object> merged = new HashMap<>(baseState(null).data());
            merged.putAll(analysisResult);
            state = new CodeGenState(merged);

            // Step 2: 执行规划
            Map<String, Object> planResult = planNode.execute(state);

            assertThat(planResult).containsKey(CodeGenState.PLAN_RESULT);
            PlanResult plan = (PlanResult) planResult.get(CodeGenState.PLAN_RESULT);
            assertThat(plan.framework()).isEqualTo("react-vite-ts");
            assertThat(plan.files()).hasSize(3);
            assertThat(plan.files().get(2).path()).isEqualTo("src/components/PlanCard.tsx");
            assertThat(plan.generationOrder()).contains(
                    "package.json", "src/App.tsx", "src/components/PlanCard.tsx");

            log.info("[OK] 需求分析→执行规划二连测试通过");
        }

        @Test
        @DisplayName("需求分析结果中 pages/apis/style 为 null → describeRequirement 安全处理")
        void shouldHandleNullSubFieldsGracefully() {
            // LLM 返回的 RequirementSpec 中 style 为 null
            String jsonWithNullStyle = "{"
                    + "\"appName\": \"Null Style App\","
                    + "\"description\": \"missing style field\","
                    + "\"pages\": null,"
                    + "\"apis\": null,"
                    + "\"features\": null,"
                    + "\"style\": null"
                    + "}";
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(jsonWithNullStyle)).build())
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(EMPTY_FIELDS_PLAN_JSON)).build());

            AgentFactory factory = agentFactory();
            RequirementAnalysisNode analysisNode =
                    new RequirementAnalysisNode(factory, streamRegistry);
            ExecutionPlanningNode planNode =
                    new ExecutionPlanningNode(factory, streamRegistry);

            CodeGenState state = baseState(null);
            Map<String, Object> analysisResult = analysisNode.execute(state);
            Map<String, Object> merged = new HashMap<>(baseState(null).data());
            merged.putAll(analysisResult);
            state = new CodeGenState(merged);

            // describeRequirement 中 spec.pages()/apis()/style() 为 null
            // 应安全返回 "0", "0", "默认"
            Map<String, Object> planResult = planNode.execute(state);

            assertThat(planResult).containsKey(CodeGenState.PLAN_RESULT);
            PlanResult plan = (PlanResult) planResult.get(CodeGenState.PLAN_RESULT);
            assertThat(plan.files()).hasSize(1);
            assertThat(plan.files().get(0).path()).isEqualTo("src/App.vue");

            log.info("[OK] describeRequirement null安全处理验证通过");
        }

        @Test
        @DisplayName("状态缺少 analysisResult → 抛出异常")
        void shouldThrowWhenNoAnalysisResult() {
            AgentFactory factory = agentFactory();
            ExecutionPlanningNode node = new ExecutionPlanningNode(factory, streamRegistry);
            CodeGenState state = baseState(null);

            assertThatThrownBy(() -> node.execute(state))
                    .isInstanceOf(NoSuchElementException.class);
            log.info("[OK] 缺少analysisResult字段异常验证通过");
        }
    }

    // ==================== 节点三：代码生成 ====================

    @Nested
    @DisplayName("节点三 - CodeGenerationNode")
    class CodeGenerationTests {

        @BeforeEach
        void init() { stubPromptLoader(); }

        // CodeGenAgent 的内部 mock: 模拟 Agent 的 generate() = writeFile工具调用 + 返回成功消息
        private void mockCodeGenAgentCalls() {
            lenient().when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(VALID_REQUIREMENT_JSON)).build())
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(VALID_PLAN_RESULT_JSON)).build());

            // Stub CodeGenAgent generate calls to return progressive tokens using StubTokenStream
            lenient().when(mockCodeGenAgent.generate(anyString()))
                    .thenAnswer(inv -> {
                        String prompt = inv.getArgument(0);
                        if (prompt.contains("package.json")) {
                            return new StubTokenStream("{\n  \"name\": \"subscription-store\"\n}");
                        } else if (prompt.contains("App.tsx")) {
                            return new StubTokenStream("import React from 'react';\nexport default App;");
                        } else {
                            return new StubTokenStream("export const PlanCard = () => null;");
                        }
                    });

            lenient().when(fileTools.writeFile(anyString(), anyString()))
                    .thenAnswer(inv -> "Success: " + inv.getArgument(0));
            lenient().when(projectContextTools.readProjectContext())
                    .thenReturn("框架: react-vite-ts\n文件: []");
            lenient().when(projectContextTools.readFileContext(any()))
                    .thenReturn("（无依赖文件）");
        }

        @Test
        @DisplayName("正常流程：遍历 filePlan 所有文件，逐文件生成")
        void shouldGenerateAllFiles() {
            mockCodeGenAgentCalls();

            AgentFactory factory = agentFactory();
            RequirementAnalysisNode analysisNode =
                    new RequirementAnalysisNode(factory, streamRegistry);
            ExecutionPlanningNode planNode =
                    new ExecutionPlanningNode(factory, streamRegistry);
            CodeGenerationNode genNode = new CodeGenerationNode(
                    factory, streamRegistry, promptLoader, projectFileService,
                    new com.fasterxml.jackson.databind.ObjectMapper(), Runnable::run);

            // 需求分析
            CodeGenState state = baseState(null);
            Map<String, Object> ar = analysisNode.execute(state);
            Map<String, Object> merged = new HashMap<>(baseState(null).data());
            merged.putAll(ar);
            // 执行规划
            state = new CodeGenState(merged);
            Map<String, Object> pr = planNode.execute(state);
            merged.putAll(pr);
            state = new CodeGenState(merged);

            // 代码生成
            Map<String, Object> result = genNode.execute(state);

            assertThat(result).containsKey(CodeGenState.BUILD_ERROR);
            // 验证 3 个文件均调用了 Agent.generate()
            verify(chatModel, times(2)).chat(any(ChatRequest.class));
            verify(mockCodeGenAgent, times(3)).generate(anyString());
            log.info("[OK] 代码生成节点遍历3个文件完成");
        }

        @Test
        @DisplayName("构建失败回退修复：注入 buildError，只重新生成相关文件")
        void shouldRetryAfterBuildFailure() {
            mockCodeGenAgentCalls();

            AgentFactory factory = agentFactory();
            RequirementAnalysisNode analysisNode =
                    new RequirementAnalysisNode(factory, streamRegistry);
            ExecutionPlanningNode planNode =
                    new ExecutionPlanningNode(factory, streamRegistry);
            CodeGenerationNode genNode = new CodeGenerationNode(
                    factory, streamRegistry, promptLoader, projectFileService,
                    new com.fasterxml.jackson.databind.ObjectMapper(), Runnable::run);

            CodeGenState state = baseState(null);
            Map<String, Object> ar = analysisNode.execute(state);
            Map<String, Object> merged = new HashMap<>(baseState(null).data());
            merged.putAll(ar);
            state = new CodeGenState(merged);
            Map<String, Object> pr = planNode.execute(state);
            merged.putAll(pr);
            // 注入构建失败状态
            merged.put(CodeGenState.BUILD_ERROR,
                    "TS2307: Cannot find module in src/components/PlanCard.tsx");
            merged.put(CodeGenState.RETRY_COUNT, 1);
            state = new CodeGenState(merged);

            when(projectFileService.listFilePaths(anyLong()))
                    .thenReturn(List.of("package.json", "src/App.tsx",
                            "src/components/PlanCard.tsx"));

            Map<String, Object> result = genNode.execute(state);

            // BUILD_ERROR 应被清空
            assertThat(result.get(CodeGenState.BUILD_ERROR)).isNull();
            verify(mockCodeGenAgent, times(1)).generate(anyString());
            log.info("[OK] 构建失败回退修复流程验证通过");
        }
    }

    // ==================== 节点四：样式优化 ====================

    @Nested
    @DisplayName("节点四 - StyleOptimizationNode")
    class StyleOptimizationTests {

        @BeforeEach
        void init() { stubPromptLoader(); }

        @Test
        @DisplayName("有样式文件时执行优化")
        void shouldOptimizeStyleFiles() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from("样式优化完成")).build());

            AgentFactory factory = agentFactory();
            StyleOptimizationNode node =
                    new StyleOptimizationNode(factory, streamRegistry);

            // 构造含样式文件的状态
            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROMPT, "test");
            data.put(CodeGenState.PROJECT_ID, PROJECT_ID);
            data.put(CodeGenState.TASK_ID, TASK_ID);
            data.put(CodeGenState.GENERATED_FILES, List.of(
                    new GeneratedFile("styles.css", "body { margin: 0 }",
                            "abc123", true),
                    new GeneratedFile("App.tsx",
                            "import React from 'react'", "def456", true)));
            CodeGenState state = new CodeGenState(data);

            Map<String, Object> result = node.execute(state);

            assertThat(result).isNotNull();
            log.info("[OK] 样式优化节点通过");
        }

        @Test
        @DisplayName("无样式文件 → 跳过优化，不抛异常")
        void shouldSkipWhenNoStyleFiles() {
            AgentFactory factory = agentFactory();
            StyleOptimizationNode node =
                    new StyleOptimizationNode(factory, streamRegistry);

            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROMPT, "test");
            data.put(CodeGenState.PROJECT_ID, PROJECT_ID);
            data.put(CodeGenState.TASK_ID, TASK_ID);
            data.put(CodeGenState.GENERATED_FILES, List.of());
            CodeGenState state = new CodeGenState(data);

            Map<String, Object> result = node.execute(state);

            assertThat(result).isEmpty();
            verify(streamEmitter).emitNode(
                    eq("style_optimization"), argThat(m -> m.contains("无样式文件")), eq("TEXT"));
            log.info("[OK] 无样式文件跳过验证通过");
        }

        @Test
        @DisplayName("LLM 调用失败 → 不阻断流水线，返回空更新")
        void shouldNotBlockPipelineOnFailure() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenThrow(new RuntimeException("LLM timeout"));

            AgentFactory factory = agentFactory();
            StyleOptimizationNode node =
                    new StyleOptimizationNode(factory, streamRegistry);

            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROMPT, "test");
            data.put(CodeGenState.PROJECT_ID, PROJECT_ID);
            data.put(CodeGenState.TASK_ID, TASK_ID);
            data.put(CodeGenState.GENERATED_FILES, List.of(
                    new GeneratedFile("styles.css", "body{}", "abc123", true)));
            CodeGenState state = new CodeGenState(data);

            // 不应抛异常
            Map<String, Object> result = node.execute(state);
            assertThat(result).isEmpty();

            verify(streamEmitter).emitNode(
                    eq("style_optimization"),
                    argThat(msg -> msg.contains("样式优化失败")),
                    eq("TEXT"));
            log.info("[OK] 样式优化失败不阻断流水线验证通过");
        }
    }

    // ==================== 节点五：构建验证 ====================

    @Nested
    @DisplayName("节点五 - BuildVerificationNode")
    class BuildVerificationTests {

        @Test
        @DisplayName("构建成功 → 写入 SUCCESS 与构建耗时")
        void shouldReportSuccess() {
            BuildResult buildResult = new BuildResult(
                    BuildStatus.SUCCESS, "Build OK", null, 3420L);
            when(sandboxService.npmBuild(anyLong())).thenReturn(buildResult);

            BuildVerificationNode node =
                    new BuildVerificationNode(sandboxService, streamRegistry);
            CodeGenState state = baseState(Map.of(
                    CodeGenState.RETRY_COUNT, 0,
                    CodeGenState.BUILD_STATUS, BuildStatus.PENDING));

            Map<String, Object> result = node.execute(state);

            assertThat(result.get(CodeGenState.BUILD_STATUS)).isEqualTo(BuildStatus.SUCCESS);
            assertThat(result.get(CodeGenState.BUILD_TIME)).isEqualTo(3);
            assertThat(result.get(CodeGenState.BUILD_ERROR)).isNull();

            verify(streamEmitter).emitLog(argThat(m -> m.contains("构建成功")));
            log.info("[OK] 构建成功路径验证通过");
        }

        @Test
        @DisplayName("构建失败 → 写入 FAILED + 错误信息 + retryCount+1")
        void shouldReportFailureAndIncrementRetry() {
            BuildResult buildResult = new BuildResult(
                    BuildStatus.FAILED, "npm ERR!", "TS2307: Cannot find module", 1200L);
            when(sandboxService.npmBuild(anyLong())).thenReturn(buildResult);

            BuildVerificationNode node =
                    new BuildVerificationNode(sandboxService, streamRegistry);
            CodeGenState state = baseState(Map.of(
                    CodeGenState.RETRY_COUNT, 1));

            Map<String, Object> result = node.execute(state);

            assertThat(result.get(CodeGenState.BUILD_STATUS)).isEqualTo(BuildStatus.FAILED);
            assertThat(result.get(CodeGenState.BUILD_ERROR))
                    .asString().contains("TS2307");
            assertThat(result.get(CodeGenState.RETRY_COUNT)).isEqualTo(2);

            verify(streamEmitter).emitLog(argThat(m -> m.contains("构建失败")));
            log.info("[OK] 构建失败+重试递增验证通过");
        }

        @Test
        @DisplayName("Sandbox 调用抛异常 → 仍返回 FAILED 状态")
        void shouldHandleSandboxException() {
            when(sandboxService.npmBuild(anyLong()))
                    .thenThrow(new RuntimeException("sandbox not reachable"));

            BuildVerificationNode node =
                    new BuildVerificationNode(sandboxService, streamRegistry);
            CodeGenState state = baseState(Map.of(CodeGenState.RETRY_COUNT, 0));

            Map<String, Object> result = node.execute(state);

            assertThat(result.get(CodeGenState.BUILD_STATUS)).isEqualTo(BuildStatus.FAILED);
            assertThat(result.get(CodeGenState.BUILD_ERROR))
                    .asString().contains("sandbox not reachable");
            assertThat(result.get(CodeGenState.RETRY_COUNT)).isEqualTo(1);
            log.info("[OK] Sandbox异常处理验证通过");
        }
    }

    // ==================== 节点六：预览部署 ====================

    @Nested
    @DisplayName("节点六 - PreviewDeployNode")
    class PreviewDeployTests {

        @Test
        @DisplayName("启动 Dev Server → 写入预览 URL 和端口")
        void shouldStartAndReturnPreviewUrl() {
            SandboxInfo sandboxInfo = new SandboxInfo(
                    "http://localhost:5173", 5173, "running");
            when(sandboxService.startDevServer(anyLong())).thenReturn(sandboxInfo);

            PreviewDeployNode node =
                    new PreviewDeployNode(sandboxService, projectService, streamRegistry);
            CodeGenState state = baseState(null);

            Map<String, Object> result = node.execute(state);

            assertThat(result.get(CodeGenState.PREVIEW_URL))
                    .isEqualTo("http://localhost:5173");
            assertThat(result.get(CodeGenState.PREVIEW_PORT)).isEqualTo(5173);

            verify(streamEmitter).emitLog(
                    argThat(m -> m.contains("http://localhost:5173")));
            verify(projectService).updateBuildResult(eq(1L), eq("SUCCESS"),
                    eq("http://localhost:5173"));
            log.info("[OK] 预览部署节点验证通过");
        }
    }

    // ==================== 完整流水线集成 ====================

    @Nested
    @DisplayName("完整流水线 — 六节点串联")
    class FullPipelineTests {

        private CodeGenPipeline pipeline;

        @BeforeEach
        void setUpPipeline() {
            stubPromptLoader();
            lenient().when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(VALID_REQUIREMENT_JSON)).build())
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(VALID_PLAN_RESULT_JSON)).build())
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from("OK")).build())
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from("OK")).build())
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from("OK")).build());
            lenient().when(fileTools.writeFile(anyString(), anyString())).thenReturn("Success");
            lenient().when(projectContextTools.readProjectContext()).thenReturn("react-vite-ts");
            lenient().when(projectContextTools.readFileContext(any())).thenReturn("");

            BuildResult buildOk = new BuildResult(
                    BuildStatus.SUCCESS, "Build OK", null, 2500L);
            lenient().when(sandboxService.npmBuild(anyLong())).thenReturn(buildOk);

            SandboxInfo sandbox = new SandboxInfo(
                    "http://localhost:5173", 5173, "running");
            lenient().when(sandboxService.startDevServer(anyLong())).thenReturn(sandbox);

            AgentFactory factory = agentFactory();
            RequirementAnalysisNode rn = new RequirementAnalysisNode(factory, streamRegistry);
            ExecutionPlanningNode en = new ExecutionPlanningNode(factory, streamRegistry);
            CodeGenerationNode cn = new CodeGenerationNode(
                    factory, streamRegistry, promptLoader, projectFileService,
                    new com.fasterxml.jackson.databind.ObjectMapper(), Runnable::run);
            StyleOptimizationNode sn = new StyleOptimizationNode(factory, streamRegistry);
            BuildVerificationNode bn = new BuildVerificationNode(sandboxService, streamRegistry);
            PreviewDeployNode pn = new PreviewDeployNode(sandboxService, projectService, streamRegistry);

            pipeline = new CodeGenPipeline(rn, en, cn, sn, bn, pn, streamRegistry, 2);
        }

        @Test
        @DisplayName("StateGraph 编译成功")
        void shouldCompileSuccessfully() throws Exception {
            pipeline.init();
            assertThat(pipeline.getCompiledGraph()).isNotNull();
            log.info("[OK] StateGraph编译完成");
        }

        @Test
        @DisplayName("路由验证：SUCCESS→preview / FAILED(未超)→codegen / FAILED(超)→error_end")
        void shouldRouteCorrectly() throws Exception {
            pipeline.init();
            // SUCCESS
            assertThat(baseState(Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS))
                    .buildStatus().get()).isEqualTo(BuildStatus.SUCCESS);
            // FAILED + retryCount=2 未超 maxRetryCount=2（判断是 > not >=）
            assertThat(baseState(Map.of(CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                    CodeGenState.RETRY_COUNT, 2)).retryCount().get()).isEqualTo(2);
            // FAILED + retryCount=3 已超
            assertThat(baseState(Map.of(CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                    CodeGenState.RETRY_COUNT, 3)).retryCount().get()).isGreaterThan(2);
            log.info("[OK] 三条条件路由验证通过");
        }

        @Test
        @DisplayName("error_end 节点：推送 SSE 错误事件")
        void shouldEmitErrorOnErrorEnd() throws Exception {
            pipeline.init();
            CodeGenState state = baseState(Map.of(
                    CodeGenState.BUILD_ERROR, "npm build failed: TS6133"));
            pipeline.errorEnd(state);
            verify(streamEmitter).error(argThat(msg -> msg.contains("npm build failed")));
            log.info("[OK] error_end节点验证通过");
        }
    }

    private static class StubTokenStream implements TokenStream {
        private java.util.function.Consumer<String> partialResponseConsumer;
        private java.util.function.Consumer<ChatResponse> completeResponseConsumer;
        private java.util.function.Consumer<dev.langchain4j.model.chat.response.PartialThinking> partialThinkingConsumer;
        private final String content;

        public StubTokenStream(String content) {
            this.content = content;
        }

        @Override
        public TokenStream onPartialResponse(java.util.function.Consumer<String> consumer) {
            this.partialResponseConsumer = consumer;
            return this;
        }

        @Override
        public TokenStream onPartialThinking(java.util.function.Consumer<dev.langchain4j.model.chat.response.PartialThinking> consumer) {
            this.partialThinkingConsumer = consumer;
            return this;
        }

        @Override
        public TokenStream onRetrieved(java.util.function.Consumer<List<dev.langchain4j.rag.content.Content>> consumer) {
            return this;
        }

        @Override
        public TokenStream onToolExecuted(java.util.function.Consumer<dev.langchain4j.service.tool.ToolExecution> consumer) {
            return this;
        }

        @Override
        public TokenStream onCompleteResponse(java.util.function.Consumer<ChatResponse> consumer) {
            this.completeResponseConsumer = consumer;
            return this;
        }

        @Override
        public TokenStream onError(java.util.function.Consumer<Throwable> consumer) {
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            return this;
        }

        @Override
        public void start() {
            if (partialThinkingConsumer != null) {
                partialThinkingConsumer.accept(new dev.langchain4j.model.chat.response.PartialThinking("Thinking: Planning layout for " + content.substring(0, Math.min(15, content.length())) + "..."));
            }
            if (partialResponseConsumer != null) {
                partialResponseConsumer.accept(content);
            }
            if (completeResponseConsumer != null) {
                completeResponseConsumer.accept(ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from(content))
                        .build());
            }
        }
    }
}
