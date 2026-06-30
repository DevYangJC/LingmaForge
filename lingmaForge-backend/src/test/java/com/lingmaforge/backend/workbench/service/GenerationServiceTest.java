package com.lingmaforge.backend.workbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.ResultCode;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenPipeline;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.entity.GenerationTaskEntity;
import com.lingmaforge.backend.workbench.mapper.ChatMessageMapper;

/**
 * GenerationService 核心流推送接口的单元测试。
 * 验证 streamGeneration 在成功、失败、以及任务不存在场景下的表现，并打印推送的 SSE 事件日志。
 */
@DisplayName("GenerationService 流推送服务测试")
@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    private static final Logger log = LoggerFactory.getLogger(GenerationServiceTest.class);

    @Mock private CodeGenPipeline pipeline;
    @Mock private AgentFactory agentFactory;
    @Mock private ProjectService projectService;
    @Mock private GenerationTaskService taskService;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private PromptTemplateLoader promptLoader;

    // 监视真实的 ObjectMapper 以拦截并记录发送的 JSON 格式数据
    private final ObjectMapper objectMapper = spy(new ObjectMapper());

    // 同步执行器，使异步线程在主线程中同步运行，方便单元测试进行状态断言
    private final Executor executor = Runnable::run;

    private GenerationService generationService;

    @BeforeEach
    void setUp() {
        generationService = new GenerationService(
                pipeline, agentFactory, projectService, taskService,
                chatMessageMapper, streamRegistry, promptLoader,
                objectMapper, executor
        );
    }

    @Test
    @DisplayName("测试流式生成成功场景 - 验证任务状态更新和事件推送")
    void testStreamGeneration_Success() throws Exception {
        String taskId = "test-task-123";
        Long projectId = 1L;
        String prompt = "创建一个简单的 React 计数器页面";

        GenerationTaskEntity taskEntity = new GenerationTaskEntity();
        taskEntity.setTaskId(taskId);
        taskEntity.setProjectId(projectId);
        taskEntity.setPrompt(prompt);
        taskEntity.setTaskType("create");
        taskEntity.setStatus("running");

        // 1. Mock 任务查询
        when(taskService.getByTaskId(taskId)).thenReturn(taskEntity);

        // 2. Mock CompiledGraph 及其执行
        CompiledGraph<CodeGenState> compiledGraph = mock(CompiledGraph.class);
        when(pipeline.getCompiledGraph()).thenReturn(compiledGraph);

        // 初始化生成的最终状态
        Map<String, Object> stateData = new HashMap<>();
        stateData.put(CodeGenState.PREVIEW_URL, "http://localhost:3000/preview/test-task-123");
        stateData.put(CodeGenState.PREVIEW_PORT, 3000);
        stateData.put(CodeGenState.BUILD_TIME, 45);
        CodeGenState finalState = new CodeGenState(stateData);

        // Mock 节点输出
        NodeOutput<CodeGenState> nodeOutput = mock(NodeOutput.class);
        when(nodeOutput.state()).thenReturn(finalState);

        List<NodeOutput<CodeGenState>> streamOutputs = Collections.singletonList(nodeOutput);
        AsyncGenerator<NodeOutput<CodeGenState>> asyncGenerator = mock(AsyncGenerator.class);
        when(asyncGenerator.iterator()).thenReturn(streamOutputs.iterator());
        when(compiledGraph.stream(anyMap())).thenReturn(asyncGenerator);

        // 3. 拦截 ObjectMapper 序列化逻辑，实时打印推送给前端的 JSON 数据
        doAnswer(invocation -> {
            String json = (String) invocation.callRealMethod();
            log.info("【测试日志 - SSE 推送 JSON 数据】: {}", json);
            return json;
        }).when(objectMapper).writeValueAsString(any());

        // 捕获注册的 Emitter
        ArgumentCaptor<GenerationStreamEmitter> emitterCaptor = ArgumentCaptor.forClass(GenerationStreamEmitter.class);

        log.info("========== [开始测试] testStreamGeneration_Success ==========");
        log.info("输入任务ID: {}", taskId);
        log.info("输入项目ID: {}", projectId);
        log.info("用户需求Prompt: {}", prompt);

        // 执行流生成
        SseEmitter sseEmitter = generationService.streamGeneration(taskId);

        // 验证 SseEmitter 非空
        assertThat(sseEmitter).isNotNull();
        log.info("1. SseEmitter 实例已成功创建并返回");

        // 验证数据库状态更新
        verify(taskService).updateStage(taskId, "requirement_analysis");
        verify(taskService).markCompleted(eq(taskId), eq("http://localhost:3000/preview/test-task-123"), eq(45));
        log.info("2. 数据库状态更新验证成功 (阶段: requirement_analysis, 已完成并保存预览URL及耗时)");

        // 验证 Stream 注册表的行为
        verify(streamRegistry).register(eq(taskId), emitterCaptor.capture());
        verify(streamRegistry).unregister(eq(taskId));
        log.info("3. 注册表注册与反注册验证成功");

        GenerationStreamEmitter capturedEmitter = emitterCaptor.getValue();
        log.info("捕获的流式推送 Emitter 实例: {}", capturedEmitter.getClass().getName());
        log.info("========== [测试完成] testStreamGeneration_Success [OK] ==========");
    }

    @Test
    @DisplayName("测试流式生成失败场景 - 验证任务状态更新为失败并推送错误事件")
    void testStreamGeneration_PipelineException() throws Exception {
        String taskId = "test-task-456";
        Long projectId = 1L;
        String prompt = "创建一个有编译错误的代码工程";

        GenerationTaskEntity taskEntity = new GenerationTaskEntity();
        taskEntity.setTaskId(taskId);
        taskEntity.setProjectId(projectId);
        taskEntity.setPrompt(prompt);
        taskEntity.setTaskType("create");
        taskEntity.setStatus("running");

        // 1. Mock 任务查询
        when(taskService.getByTaskId(taskId)).thenReturn(taskEntity);

        // 2. Mock CompiledGraph 并模拟抛出异常
        CompiledGraph<CodeGenState> compiledGraph = mock(CompiledGraph.class);
        when(pipeline.getCompiledGraph()).thenReturn(compiledGraph);
        when(compiledGraph.stream(anyMap())).thenThrow(new RuntimeException("LLM 服务响应超时，请重试"));

        // 3. 拦截序列化打印错误消息推送
        doAnswer(invocation -> {
            String json = (String) invocation.callRealMethod();
            log.info("【测试日志 - SSE 推送错误 JSON 数据】: {}", json);
            return json;
        }).when(objectMapper).writeValueAsString(any());

        log.info("========== [开始测试] testStreamGeneration_PipelineException ==========");
        log.info("输入任务ID: {}", taskId);

        // 执行流生成
        SseEmitter sseEmitter = generationService.streamGeneration(taskId);

        // 验证 SseEmitter 非空
        assertThat(sseEmitter).isNotNull();

        // 验证失败时的数据库更新及注销流程
        verify(taskService).updateStage(taskId, "requirement_analysis");
        verify(taskService).markFailed(eq(taskId), eq("LLM 服务响应超时，请重试"));
        verify(streamRegistry).unregister(eq(taskId));

        log.info("1. 数据库任务更新为失败 (错误原因: LLM 服务响应超时，请重试) 验证成功");
        log.info("2. 注册表反注册清理验证成功");
        log.info("========== [测试完成] testStreamGeneration_PipelineException [OK] ==========");
    }

    @Test
    @DisplayName("测试任务未找到场景 - 抛出 BusinessException 异常")
    void testStreamGeneration_TaskNotFound() {
        String taskId = "non-existent-task-uuid";
        when(taskService.getByTaskId(taskId)).thenReturn(null);

        log.info("========== [开始测试] testStreamGeneration_TaskNotFound ==========");
        log.info("输入不存在的任务ID: {}", taskId);

        // 验证抛出业务异常且状态码为 TASK_NOT_FOUND
        assertThatThrownBy(() -> generationService.streamGeneration(taskId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.TASK_NOT_FOUND.getCode());

        log.info("1. 成功拦截异常: 任务未找到时的 BusinessException 验证成功");
        log.info("========== [测试完成] testStreamGeneration_TaskNotFound [OK] ==========");
    }
}
