package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.Map;

import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.workbench.ai.node.BuildVerificationNode;
import com.lingmaforge.backend.workbench.ai.node.CodeGenerationNode;
import com.lingmaforge.backend.workbench.ai.node.PreviewDeployNode;
import com.lingmaforge.backend.workbench.ai.node.RequirementAnalysisNode;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenPipeline;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("CodeGenPipeline 4 节点 agentic")
@ExtendWith(MockitoExtension.class)
class CodeGenPipelineTest {

    private static final String TASK_ID = "pipeline-test-task";

    @Mock private RequirementAnalysisNode requirementAnalysisNode;
    @Mock private CodeGenerationNode codeGenerationNode;
    @Mock private BuildVerificationNode buildVerificationNode;
    @Mock private PreviewDeployNode previewDeployNode;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private GenerationStreamEmitter streamEmitter;

    private CodeGenPipeline pipeline;

    @BeforeEach
    void setUp() {
        lenient().when(streamRegistry.get(TASK_ID)).thenReturn(streamEmitter);
        pipeline = new CodeGenPipeline(
                requirementAnalysisNode, codeGenerationNode,
                buildVerificationNode, previewDeployNode, streamRegistry, 2);
    }

    @Test
    @DisplayName("StateGraph 编译")
    void shouldCompileGraph() throws Exception {
        pipeline.init();
        assertThat(pipeline.getCompiledGraph()).isNotNull();
    }

    @Test
    @DisplayName("路由：成功→preview 失败可重试→code_gen 超限→error")
    void shouldRouteAfterBuild() {
        assertThat(pipeline.routeAfterBuild(state(Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS))))
                .isEqualTo(PreviewDeployNode.NODE_NAME);
        assertThat(pipeline.routeAfterBuild(state(Map.of(
                CodeGenState.BUILD_STATUS, BuildStatus.FAILED, CodeGenState.RETRY_COUNT, 2))))
                .isEqualTo(CodeGenerationNode.NODE_NAME);
        assertThat(pipeline.routeAfterBuild(state(Map.of(
                CodeGenState.BUILD_STATUS, BuildStatus.FAILED, CodeGenState.RETRY_COUNT, 3))))
                .isEqualTo(CodeGenPipeline.ERROR_END);
    }

    @Test
    @DisplayName("error_end 推 SSE 错误")
    void shouldEmitErrorOnErrorEnd() {
        pipeline.errorEnd(state(Map.of(CodeGenState.BUILD_ERROR, "npm build failed")));
        org.mockito.Mockito.verify(streamEmitter)
                .error(org.mockito.ArgumentMatchers.contains("npm build failed"));
    }

    private CodeGenState state(Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>();
        data.put(CodeGenState.PROJECT_ID, "1");
        data.put(CodeGenState.TASK_ID, TASK_ID);
        data.put(CodeGenState.PROMPT, "创建一个电商应用");
        data.putAll(extra);
        return new CodeGenState(data);
    }
}