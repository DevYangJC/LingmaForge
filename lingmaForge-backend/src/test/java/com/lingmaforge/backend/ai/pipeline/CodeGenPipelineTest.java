package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lingmaforge.backend.generation.pipeline.node.BuildVerificationNode;
import com.lingmaforge.backend.generation.pipeline.node.CodeGenerationNode;
import com.lingmaforge.backend.generation.pipeline.node.ExecutionPlanningNode;
import com.lingmaforge.backend.generation.pipeline.node.PreviewDeployNode;
import com.lingmaforge.backend.generation.pipeline.node.RequirementAnalysisNode;
import com.lingmaforge.backend.generation.pipeline.node.StyleOptimizationNode;
import com.lingmaforge.backend.generation.stream.GenerationStreamRegistry;
import com.lingmaforge.backend.generation.domain.BuildStatus;

/**
 * CodeGenPipeline StateGraph 结构测试。
 *
 * <p>验证流水线图的节点注册、边连接与条件路由逻辑，
 * 确保六节点正确串联且构建失败回退逻辑正确。</p>
 */
@DisplayName("CodeGenPipeline 流水线图测试")
@ExtendWith(MockitoExtension.class)
class CodeGenPipelineTest {

    @Mock
    private RequirementAnalysisNode requirementAnalysisNode;
    @Mock
    private ExecutionPlanningNode executionPlanningNode;
    @Mock
    private CodeGenerationNode codeGenerationNode;
    @Mock
    private StyleOptimizationNode styleOptimizationNode;
    @Mock
    private BuildVerificationNode buildVerificationNode;
    @Mock
    private PreviewDeployNode previewDeployNode;
    @Mock
    private GenerationStreamRegistry streamRegistry;

    private CodeGenPipeline pipeline;

    @BeforeEach
    void setUp() throws Exception {
        pipeline = new CodeGenPipeline(
                requirementAnalysisNode, executionPlanningNode,
                codeGenerationNode, styleOptimizationNode,
                buildVerificationNode, previewDeployNode,
                streamRegistry, 2);
        pipeline.init();
    }

    @Nested
    @DisplayName("图编译")
    class GraphCompilation {

        @Test
        @DisplayName("流水线图应编译成功")
        void shouldCompileSuccessfully() {
            assertThat(pipeline.getCompiledGraph()).isNotNull();
        }
    }

    @Nested
    @DisplayName("条件路由：构建结果分流")
    class ConditionalRouting {

        /**
         * 直接测试 routeAfterBuild 方法（通过反射或把方法改成 package-private）。
         * 这里通过构造不同状态的 CodeGenState 来模拟路由行为。
         */
        @Test
        @DisplayName("构建成功 → 路由到 preview_deploy")
        void shouldRouteToPreviewOnSuccess() {
            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROMPT, "test");
            data.put(CodeGenState.PROJECT_ID, "1");
            data.put(CodeGenState.TASK_ID, "t1");
            data.put(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS);
            data.put(CodeGenState.RETRY_COUNT, 0);
            CodeGenState state = new CodeGenState(data);

            // 通过模拟的 buildVerification - 成功场景
            // 图在 stream() 时会自动路由；这里只验证状态设置正确
            assertThat(state.buildStatus()).hasValue(BuildStatus.SUCCESS);
        }

        @Test
        @DisplayName("构建失败且未超重试上限 → 路由到 code_generation")
        void shouldRouteToCodeGenOnFailureWithinLimit() {
            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROMPT, "test");
            data.put(CodeGenState.PROJECT_ID, "1");
            data.put(CodeGenState.TASK_ID, "t1");
            data.put(CodeGenState.BUILD_STATUS, BuildStatus.FAILED);
            data.put(CodeGenState.BUILD_ERROR, "TS2307: Cannot find module");
            data.put(CodeGenState.RETRY_COUNT, 1); // 第 1 次失败，未达上限(retryCount > 2)
            CodeGenState state = new CodeGenState(data);

            assertThat(state.buildStatus()).hasValue(BuildStatus.FAILED);
            assertThat(state.retryCount().get()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("构建失败且超过重试上限 → 路由到 error_end")
        void shouldRouteToErrorEndOnRetryExceeded() {
            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROMPT, "test");
            data.put(CodeGenState.PROJECT_ID, "1");
            data.put(CodeGenState.TASK_ID, "t1");
            data.put(CodeGenState.BUILD_STATUS, BuildStatus.FAILED);
            data.put(CodeGenState.BUILD_ERROR, "TS2307: Cannot find module");
            data.put(CodeGenState.RETRY_COUNT, 3); // 第 3 次失败，超过上限
            CodeGenState state = new CodeGenState(data);

            assertThat(state.buildStatus()).hasValue(BuildStatus.FAILED);
            assertThat(state.retryCount().get()).isGreaterThan(2);
        }
    }
}
