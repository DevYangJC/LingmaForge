package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import com.lingmaforge.backend.workbench.ai.node.*;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenPipeline;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CodeGenPipeline StateGraph 结构测试。
 *
 * <p>验证流水线图的节点注册、边连接与条件路由逻辑，
 * 确保六节点正确串联且构建失败回退逻辑正确。</p>
 */
@DisplayName("CodeGenPipeline 流水线图测试")
@ExtendWith(MockitoExtension.class)
class CodeGenPipelineTest {

    private static final Logger log = LoggerFactory.getLogger(CodeGenPipelineTest.class);

    @Mock private RequirementAnalysisNode requirementAnalysisNode;
    @Mock private ExecutionPlanningNode executionPlanningNode;
    @Mock private CodeGenerationNode codeGenerationNode;
    @Mock private StyleOptimizationNode styleOptimizationNode;
    @Mock private BuildVerificationNode buildVerificationNode;
    @Mock private PreviewDeployNode previewDeployNode;
    @Mock private GenerationStreamRegistry streamRegistry;

    private CodeGenPipeline pipeline;

    @BeforeEach
    void setUp() throws Exception {
        pipeline = new CodeGenPipeline(
                requirementAnalysisNode, executionPlanningNode,
                codeGenerationNode, styleOptimizationNode,
                buildVerificationNode, previewDeployNode,
                streamRegistry, 2);
        pipeline.init();

        log.info("========== CodeGenPipeline 初始化 ==========");
        log.info("  节点链: 需求分析 -> 执行规划 -> 代码生成 -> 样式优化 -> 构建验证 -> 预览部署");
        log.info("  最大重试次数: 2");
        log.info("  条件路由: 构建验证 -> { 成功: 预览部署 } / { 失败+未超限: 代码生成 } / { 失败+超限: 终止 }");
        log.info("===========================================");
    }

    @Nested
    @DisplayName("图编译")
    class GraphCompilation {
        @Test
        @DisplayName("流水线图应编译成功")
        void shouldCompileSuccessfully() {
            assertThat(pipeline.getCompiledGraph()).isNotNull();
            log.info("[OK] 流水线图编译成功，通道数: {}",
                    pipeline.getCompiledGraph().stateGraph.getChannels().size());
        }
    }

    @Nested
    @DisplayName("条件路由：构建结果分流")
    class ConditionalRouting {

        /**
         * 构造一个携带指定构建状态与重试次数的 CodeGenState。
         */
        private CodeGenState stateWith(BuildStatus status, int retryCount) {
            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROMPT, "test");
            data.put(CodeGenState.PROJECT_ID, "1");
            data.put(CodeGenState.TASK_ID, "t1");
            data.put(CodeGenState.BUILD_STATUS, status);
            data.put(CodeGenState.RETRY_COUNT, retryCount);
            return new CodeGenState(data);
        }

        @Test
        @DisplayName("构建成功 -> 路由到预览部署节点")
        void shouldRouteToPreviewOnSuccess() {
            CodeGenState state = stateWith(BuildStatus.SUCCESS, 0);

            log.info("--- 模拟构建成功场景 ---");
            log.info("  BUILD_STATUS: SUCCESS, RETRY_COUNT: 0");
            log.info("  期望路由: build_verification -> preview_deploy");

            String route = pipeline.routeAfterBuild(state);
            assertThat(route).isEqualTo(PreviewDeployNode.NODE_NAME);
            log.info("  [OK] 路由正确：构建成功 -> 预览部署节点 ({})", route);
        }

        @Test
        @DisplayName("构建失败且未超重试上限(retry=0) -> 路由到代码生成节点")
        void shouldRouteToCodeGenOnFirstFailure() {
            CodeGenState state = stateWith(BuildStatus.FAILED, 0);

            log.info("--- 模拟构建失败（首次，可重试）场景 ---");
            log.info("  BUILD_STATUS: FAILED, RETRY_COUNT: 0 (上限: 2)");
            log.info("  期望路由: build_verification -> code_generation (回退重试)");

            String route = pipeline.routeAfterBuild(state);
            assertThat(route).isEqualTo(CodeGenerationNode.NODE_NAME);
            log.info("  [OK] 路由正确：首次失败 -> 回退到代码生成节点 ({})", route);
        }

        @Test
        @DisplayName("构建失败且未超重试上限(retry=2=上限) -> 路由到代码生成节点")
        void shouldRouteToCodeGenAtLimitBoundary() {
            CodeGenState state = stateWith(BuildStatus.FAILED, 2);

            log.info("--- 模拟构建失败（边界，retry=2=max）场景 ---");
            log.info("  BUILD_STATUS: FAILED, RETRY_COUNT: 2 (上限: 2，未超限)");
            log.info("  期望路由: build_verification -> code_generation (仍可回退)");

            String route = pipeline.routeAfterBuild(state);
            assertThat(route).isEqualTo(CodeGenerationNode.NODE_NAME);
            log.info("  [OK] 路由正确：retry=max 仍未超限 -> 回退到代码生成节点 ({})", route);
        }

        @Test
        @DisplayName("构建失败且超过重试上限(retry=3>max=2) -> 路由到终止节点")
        void shouldRouteToErrorEndOnRetryExceeded() {
            CodeGenState state = stateWith(BuildStatus.FAILED, 3);

            log.info("--- 模拟构建失败（超限）场景 ---");
            log.info("  BUILD_STATUS: FAILED, RETRY_COUNT: 3 (上限: 2) -> 已超限");
            log.info("  期望路由: build_verification -> error_end (终止)");

            String route = pipeline.routeAfterBuild(state);
            assertThat(route).isEqualTo(CodeGenPipeline.ERROR_END);
            log.info("  [OK] 路由正确：重试次数超限 -> 终止于 error_end ({})", route);
        }

        @Test
        @DisplayName("构建状态缺失时按失败处理 -> 路由到代码生成节点")
        void shouldTreatMissingBuildStatusAsFailure() {
            Map<String, Object> data = new HashMap<>();
            data.put(CodeGenState.PROMPT, "test");
            data.put(CodeGenState.PROJECT_ID, "1");
            data.put(CodeGenState.TASK_ID, "t1");
            data.put(CodeGenState.RETRY_COUNT, 0);
            CodeGenState state = new CodeGenState(data);

            log.info("--- 模拟构建状态缺失场景 ---");
            log.info("  BUILD_STATUS: (缺失), RETRY_COUNT: 0");
            log.info("  期望路由: routeAfterBuild 回退到 FAILED 分支 -> code_generation");

            String route = pipeline.routeAfterBuild(state);
            assertThat(route).isEqualTo(CodeGenerationNode.NODE_NAME);
            log.info("  [OK] 缺失状态按失败处理 -> 回退到代码生成节点 ({})", route);
        }
    }
}
