package com.lingmaforge.backend.workbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenPipeline;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.pipeline.IterationPipeline;
import com.lingmaforge.backend.workbench.entity.GenerationTaskEntity;
import com.lingmaforge.backend.workbench.mapper.ChatMessageMapper;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 生成服务的对话式迭代入口测试。
 *
 * <p>验证 `/api/stream/iteration/{taskId}` 背后使用新的 {@link IterationPipeline}，
 * 而不是旧的直接 IterationAgent 短流程。</p>
 */
@DisplayName("GenerationService 对话式迭代")
@ExtendWith(MockitoExtension.class)
class GenerationServiceIterationTest {

    @Mock private CodeGenPipeline codeGenPipeline;
    @Mock private IterationPipeline iterationPipeline;
    @Mock private AgentFactory agentFactory;
    @Mock private ProjectService projectService;
    @Mock private GenerationTaskService taskService;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private PromptTemplateLoader promptLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor executor = Runnable::run;
    private GenerationService generationService;

    @BeforeEach
    void setUp() {
        generationService = new GenerationService(
                codeGenPipeline,
                iterationPipeline,
                agentFactory,
                projectService,
                taskService,
                chatMessageMapper,
                streamRegistry,
                promptLoader,
                objectMapper,
                executor);
    }

    @Test
    @DisplayName("streamIteration 使用 IterationPipeline 并传入迭代黑板字段")
    void streamIterationShouldUseIterationPipeline() throws Exception {
        String taskId = "iteration-task-123";
        Long projectId = 1L;
        String prompt = "把首页 CTA 改成绿色";
        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setTaskId(taskId);
        task.setProjectId(projectId);
        task.setPrompt(prompt);
        task.setTaskType("iterate");
        task.setStatus("running");
        when(taskService.getByTaskId(taskId)).thenReturn(task);

        CompiledGraph<CodeGenState> compiledGraph = mock(CompiledGraph.class);
        when(iterationPipeline.getCompiledGraph()).thenReturn(compiledGraph);
        Map<String, Object> finalData = new HashMap<>();
        finalData.put(CodeGenState.PREVIEW_URL, "http://localhost:5173");
        finalData.put(CodeGenState.PREVIEW_PORT, 5173);
        finalData.put(CodeGenState.BUILD_TIME, 8);
        NodeOutput<CodeGenState> nodeOutput = mock(NodeOutput.class);
        when(nodeOutput.state()).thenReturn(new CodeGenState(finalData));
        AsyncGenerator<NodeOutput<CodeGenState>> asyncGenerator = mock(AsyncGenerator.class);
        when(asyncGenerator.iterator()).thenReturn(List.of(nodeOutput).iterator());
        when(compiledGraph.stream(anyMap())).thenReturn(asyncGenerator);

        Flux<ServerSentEvent<String>> sseFlux = generationService.streamIteration(taskId);

        assertThat(sseFlux).isNotNull();
        // 订阅以触发内部执行（Flux.create 是懒执行）
        sseFlux.subscribe();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(compiledGraph).stream(inputCaptor.capture());
        Map<String, Object> inputs = inputCaptor.getValue();
        assertThat(inputs)
                .containsEntry(CodeGenState.PROJECT_ID, String.valueOf(projectId))
                .containsEntry(CodeGenState.TASK_ID, taskId)
                .containsEntry(CodeGenState.ITERATION_PROMPT, prompt);
        verify(taskService).updateStage(taskId, "iteration_intent_analysis");
        verify(taskService).markCompleted(eq(taskId), eq("http://localhost:5173"), eq(8));
        verify(agentFactory, never()).createIterationAgent();
        verify(streamRegistry).register(eq(taskId), any(GenerationStreamEmitter.class));
        verify(streamRegistry).unregister(taskId);
    }
}
