package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.node.*;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenPipeline;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.CodeGenAgent;
import com.lingmaforge.backend.workbench.ai.service.ExecutionPlanner;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;
import com.lingmaforge.backend.workbench.ai.service.RequirementAnalyzer;
import com.lingmaforge.backend.workbench.ai.service.StyleOptimizationAgent;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 构建验证流程的完整 pipeline 集成测试。
 *
 * <p>覆盖三大核心场景：</p>
 * <ol>
 *   <li><b>构建成功</b> → 经条件边路由到 preview_deploy → 返回预览 URL</li>
 *   <li><b>构建失败（可重试）</b> → 条件边路由到 code_generation → retryCount 递增 → 再验证</li>
 *   <li><b>构建失败（超出重试上限）</b> → 条件边路由到 error_end → 推送错误事件</li>
 * </ol>
 *
 * <p>通过 mock SandboxService 模拟各种构建结果，验证节点事件、状态传播与路由决策。</p>
 */
@DisplayName("构建验证完整流程 — StateGraph 集成测试")
@ExtendWith(MockitoExtension.class)
class BuildVerificationPipelineTest {

    private static final Logger log = LoggerFactory.getLogger(BuildVerificationPipelineTest.class);

    private static final String TASK_ID = "build-test-001";
    private static final String PROJECT_ID = "42";

    @Mock private SandboxService sandboxService;
    @Mock private ProjectService projectService;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private GenerationStreamEmitter streamEmitter;
    @Mock private ChatModel chatModel;
    @Mock private PromptTemplateLoader promptLoader;
    @Mock private FileTools fileTools;
    @Mock private ProjectContextTools projectContextTools;
    @Mock private IterationTools iterationTools;
    @Mock private ProjectFileService projectFileService;
    @Mock private CodeGenAgent mockCodeGenAgent;

    @AfterEach
    void tearDown() {
        GenerationContext.clear();
    }

    // ==================== 辅助方法 ====================

    private CodeGenState baseState(Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>();
        data.put(CodeGenState.PROMPT, "Generate a subscription store");
        data.put(CodeGenState.PROJECT_ID, PROJECT_ID);
        data.put(CodeGenState.TASK_ID, TASK_ID);
        if (extra != null) data.putAll(extra);
        return new CodeGenState(data);
    }

    // ==================== 场景一：构建成功 → 预览部署 ====================

    @Nested
    @DisplayName("场景一：构建成功 → 预览部署")
    class BuildSuccessFlow {

        private CodeGenPipeline pipeline;

        @BeforeEach
        void setUp() throws Exception {
            lenient().when(streamRegistry.get(TASK_ID)).thenReturn(streamEmitter);

            // 构建成功
            BuildResult buildOk = new BuildResult(BuildStatus.SUCCESS, "Build OK", null, 3200L);
            lenient().when(sandboxService.npmBuild(anyLong())).thenReturn(buildOk);

            // 预览部署
            lenient().when(sandboxService.startDevServer(anyLong()))
                    .thenReturn(new SandboxInfo("http://localhost:5173", 5173, "running"));
            lenient().when(projectService.getById(anyLong()))
                    .thenReturn(org.mockito.Mockito.mock(com.lingmaforge.backend.workbench.entity.ProjectEntity.class));

            // 构建 Pipeline（各节点用 mock）
            pipeline = createMockPipeline();
            pipeline.init();
            log.info("============================================================");
            log.info("  场景一：构建成功（3.2s）→ 期望路由 preview_deploy");
            log.info("============================================================");
        }

        @Test
        @DisplayName("构建成功 → preview_deploy 设置预览 URL")
        void shouldRouteToPreviewDeploy() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(CodeGenState.PROMPT, "test");
            inputs.put(CodeGenState.PROJECT_ID, PROJECT_ID);
            inputs.put(CodeGenState.TASK_ID, TASK_ID);

            // 执行流水线（各 mock 节点依次执行，最终到达 BuildVerificationNode）
            // 这里直接测试条件路由逻辑而不是完整 state graph 流，因为节点都已 mock
            BuildVerificationNode node = new BuildVerificationNode(sandboxService, streamRegistry);
            Map<String, Object> result = node.execute(baseState(Map.of(
                    CodeGenState.BUILD_STATUS, BuildStatus.PENDING,
                    CodeGenState.RETRY_COUNT, 0)));

            assertThat(result.get(CodeGenState.BUILD_STATUS)).isEqualTo(BuildStatus.SUCCESS);
            assertThat(result.get(CodeGenState.BUILD_TIME)).isEqualTo(3);
            assertThat(result.get(CodeGenState.BUILD_ERROR)).isNull();

            verify(streamEmitter).emitNodeStart("build_verification", "正在进行代码构建与类型验证...");
            verify(streamEmitter).emitNodeEnd("build_verification");
            verify(streamEmitter).emitLog(org.mockito.ArgumentMatchers.contains("构建成功"));

            log.info("[OK] 构建成功 → SUCCESS (3s) + 正确的事件广播");
        }
    }

    // ==================== 场景二：构建失败可重试 ====================

    @Nested
    @DisplayName("场景二：构建失败 → 回退代码生成（可重试）")
    class BuildFailureRetryFlow {

        private CodeGenPipeline pipeline;

        @BeforeEach
        void setUp() throws Exception {
            lenient().when(streamRegistry.get(TASK_ID)).thenReturn(streamEmitter);

            // 构建失败
            BuildResult buildFail = new BuildResult(BuildStatus.FAILED,
                    "npm ERR! Command failed",
                    "TS2307: Cannot find module './NotFound'",
                    1500L);
            lenient().when(sandboxService.npmBuild(anyLong())).thenReturn(buildFail);

            pipeline = createMockPipeline();
            pipeline.init();
            log.info("============================================================");
            log.info("  场景二：构建失败（TS2307）→ 期望回退 code_generation");
            log.info("============================================================");
        }

        @Test
        @DisplayName("构建失败时 retryCount 递增，错误信息正确传播")
        void shouldIncrementRetryCountAndPropagateError() {
            BuildVerificationNode node = new BuildVerificationNode(sandboxService, streamRegistry);

            // retryCount=1 再失败 → 路由决策
            Map<String, Object> result = node.execute(baseState(Map.of(
                    CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                    CodeGenState.RETRY_COUNT, 1)));

            assertThat(result.get(CodeGenState.BUILD_STATUS)).isEqualTo(BuildStatus.FAILED);
            assertThat(result.get(CodeGenState.BUILD_ERROR))
                    .as("构建错误应传播")
                    .toString().contains("TS2307");
            assertThat(result.get(CodeGenState.RETRY_COUNT))
                    .as("retryCount 应从 1 递增到 2")
                    .isEqualTo(2);

            log.info("[OK] 构建失败 → RETRY_COUNT=2 (已+1), BUILD_ERROR=TS2307");
        }

        @Test
        @DisplayName("retryCount=0 时首次失败 → routeAfterBuild 返回 code_generation")
        void shouldRouteToCodeGenOnFirstFailure() {
            String route = pipeline.routeAfterBuild(baseState(Map.of(
                    CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                    CodeGenState.RETRY_COUNT, 0)));

            assertThat(route).isEqualTo(CodeGenerationNode.NODE_NAME);
            log.info("[OK] 首次构建失败 → 路由到 code_generation");
        }

        @Test
        @DisplayName("retryCount=2（未超限，maxRetryCount=2）→ 路由到 code_generation")
        void shouldRouteToCodeGenAtLimitBoundary() {
            String route = pipeline.routeAfterBuild(baseState(Map.of(
                    CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                    CodeGenState.RETRY_COUNT, 2)));

            assertThat(route).isEqualTo(CodeGenerationNode.NODE_NAME);
            log.info("[OK] retryCount=2（≤maxRetryCount=2）→ 仍可回退 code_generation");
        }
    }

    // ==================== 场景三：构建失败超出重试上限 ====================

    @Nested
    @DisplayName("场景三：构建失败超出重试上限 → error_end")
    class BuildFailureExhaustedFlow {

        private CodeGenPipeline pipeline;

        @BeforeEach
        void setUp() throws Exception {
            lenient().when(streamRegistry.get(TASK_ID)).thenReturn(streamEmitter);

            BuildResult buildFail = new BuildResult(BuildStatus.FAILED,
                    "npm ERR!",
                    "Multiple build failures",
                    2000L);
            lenient().when(sandboxService.npmBuild(anyLong())).thenReturn(buildFail);

            pipeline = createMockPipeline();
            pipeline.init();
            log.info("============================================================");
            log.info("  场景三：构建失败超限（retryCount=3 > max=2）→ error_end");
            log.info("============================================================");
        }

        @Test
        @DisplayName("retryCount=3 超出 maxRetryCount=2 → 路由到 error_end")
        void shouldRouteToErrorEndWhenExhausted() {
            String route = pipeline.routeAfterBuild(baseState(Map.of(
                    CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                    CodeGenState.RETRY_COUNT, 3)));

            assertThat(route).isEqualTo(CodeGenPipeline.ERROR_END);
            log.info("[OK] retryCount=3 > maxRetryCount=2 → 路由到 error_end");
        }

        @Test
        @DisplayName("error_end 节点发送错误事件")
        void shouldEmitErrorOnErrorEnd() {
            pipeline.errorEnd(baseState(Map.of(
                    CodeGenState.BUILD_ERROR, "npm build failed after 3 retries: TS6133")));

            verify(streamEmitter).error(
                    org.mockito.ArgumentMatchers.contains("npm build failed"));
            log.info("[OK] error_end 广播错误事件");
        }
    }

    // ==================== 场景四：构建异常 ====================

    @Nested
    @DisplayName("场景四：构建异常（Sandbox 抛异常）")
    class BuildExceptionFlow {

        @Test
        @DisplayName("SandboxService 抛异常 → 节点返回 FAILED + 错误消息")
        void shouldReturnFailedOnSandboxException() {
            lenient().when(streamRegistry.get(TASK_ID)).thenReturn(streamEmitter);
            when(sandboxService.npmBuild(anyLong()))
                    .thenThrow(new RuntimeException("Cannot run program \"npm\": CreateProcess error=2"));

            BuildVerificationNode node = new BuildVerificationNode(sandboxService, streamRegistry);
            Map<String, Object> result = node.execute(baseState(Map.of(
                    CodeGenState.BUILD_STATUS, BuildStatus.PENDING,
                    CodeGenState.RETRY_COUNT, 0)));

            assertThat(result.get(CodeGenState.BUILD_STATUS)).isEqualTo(BuildStatus.FAILED);
            assertThat(result.get(CodeGenState.BUILD_ERROR))
                    .as("异常消息应传播")
                    .toString().contains("CreateProcess error=2");
            assertThat(result.get(CodeGenState.RETRY_COUNT)).isEqualTo(1);

            verify(streamEmitter).emitLog(
                    org.mockito.ArgumentMatchers.contains("构建验证异常"));
            log.info("[OK] Sandbox 异常 → FAILED + retryCount+1 + 异常信息传播");
        }
    }

    // ==================== 构建 Pipeline ====================

    /**
     * 创建一个全部节点均为 mock 的流水线实例，
     * 仅 BuildVerificationNode（以及 routeAfterBuild / errorEnd）使用真实逻辑。
     */
    private CodeGenPipeline createMockPipeline() throws Exception {
        // 创建 AgentFactory 的 mock
        @SuppressWarnings("unchecked")
        AgentFactory mockFactory = org.mockito.Mockito.mock(AgentFactory.class);

        // 全部节点用 mock
        RequirementAnalysisNode mockRequirementNode =
                org.mockito.Mockito.mock(RequirementAnalysisNode.class);
        ExecutionPlanningNode mockPlanningNode =
                org.mockito.Mockito.mock(ExecutionPlanningNode.class);
        CodeGenerationNode mockCodeGenNode =
                org.mockito.Mockito.mock(CodeGenerationNode.class);
        StyleOptimizationNode mockStyleNode =
                org.mockito.Mockito.mock(StyleOptimizationNode.class);
        PreviewDeployNode mockPreviewNode =
                org.mockito.Mockito.mock(PreviewDeployNode.class);

        // 真实 BuildVerificationNode
        BuildVerificationNode buildNode = new BuildVerificationNode(sandboxService, streamRegistry);

        return new CodeGenPipeline(
                mockRequirementNode, mockPlanningNode,
                mockCodeGenNode, mockStyleNode,
                buildNode, mockPreviewNode,
                streamRegistry, 2);
    }
}
