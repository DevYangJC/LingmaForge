package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.Map;

import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.workbench.ai.node.BuildVerificationNode;
import com.lingmaforge.backend.workbench.ai.node.CodePatchNode;
import com.lingmaforge.backend.workbench.ai.node.PreviewDeployNode;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.pipeline.IterationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 对话式迭代 agentic pipeline 测试——4 节点收敛版。
 */
@DisplayName("对话式迭代 agentic pipeline")
@ExtendWith(MockitoExtension.class)
class IterationPipelineTest {

    private static final String TASK_ID = "iteration-pipeline-task";

    @Mock private CodePatchNode codePatchNode;
    @Mock private BuildVerificationNode buildVerificationNode;
    @Mock private PreviewDeployNode previewDeployNode;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private GenerationStreamEmitter streamEmitter;

    private IterationPipeline pipeline;

    @BeforeEach
    void setUp() {
        lenient().when(streamRegistry.get(TASK_ID)).thenReturn(streamEmitter);
        pipeline = new IterationPipeline(
                codePatchNode,
                buildVerificationNode,
                previewDeployNode,
                streamRegistry,
                2);
    }

    @Test
    @DisplayName("StateGraph 可以成功编译")
    void shouldCompileGraph() throws Exception {
        pipeline.init();

        assertThat(pipeline.getCompiledGraph()).isNotNull();
    }

    @Test
    @DisplayName("构建成功 → preview_deploy；失败可重试 → code_patch；超限 → error_end")
    void shouldRouteAfterBuild() {
        assertThat(pipeline.routeAfterBuild(state(Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS))))
                .isEqualTo(PreviewDeployNode.NODE_NAME);
        assertThat(pipeline.routeAfterBuild(state(Map.of(
                CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                CodeGenState.RETRY_COUNT, 2))))
                .isEqualTo(CodePatchNode.NODE_NAME);
        assertThat(pipeline.routeAfterBuild(state(Map.of(
                CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                CodeGenState.RETRY_COUNT, 3))))
                .isEqualTo(IterationPipeline.ERROR_END);
    }

    @Test
    @DisplayName("error_end 会向前端推送迭代失败事件")
    void shouldEmitErrorOnErrorEnd() {
        pipeline.errorEnd(state(Map.of(CodeGenState.BUILD_ERROR, "npm build failed")));

        org.mockito.Mockito.verify(streamEmitter)
                .error(org.mockito.ArgumentMatchers.contains("npm build failed"));
    }

    private CodeGenState state(Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>();
        data.put(CodeGenState.PROJECT_ID, "1");
        data.put(CodeGenState.TASK_ID, TASK_ID);
        data.put(CodeGenState.ITERATION_PROMPT, "修改首页按钮");
        data.putAll(extra);
        return new CodeGenState(data);
    }
}