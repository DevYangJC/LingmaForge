package com.lingmaforge.backend.workbench.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.bsc.langgraph4j.NodeOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenPipeline;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.pipeline.IterationPipeline;
import com.lingmaforge.backend.workbench.ai.stream.FluxStreamEmitter;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.ResultCode;
import com.lingmaforge.backend.infra.config.AsyncConfig;
import com.lingmaforge.backend.workbench.entity.ChatMessageEntity;
import com.lingmaforge.backend.workbench.mapper.ChatMessageMapper;

import jakarta.annotation.PreDestroy;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 生成服务：编排 StateGraph 流水线并通过响应式 SSE 流式推送进度。
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建生成任务，分配 taskId（= StateGraph threadId = SSE streamId）</li>
 *   <li>异步驱动 {@link CodeGenPipeline} / {@link IterationPipeline} 的 graph.stream()</li>
 *   <li>把节点进度、文件生成、日志、完成/错误事件经 {@link FluxStreamEmitter} 统一封装后
 *       通过 {@code Flux<ServerSentEvent<String>>}（Spring WebFlux 响应式返回值）推送给前端</li>
 *   <li>支持停止生成、迭代修改</li>
 * </ul>
 * 每个任务对应一个 {@link FluxStreamEmitter}（实现 {@link
 * com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter}）与一个 {@link StreamContext}。</p>
 *
 * <p><b>统一封装事件类型</b>（SSE {@code event:} 名）：token / thinking / tool_call / done / error。
 * 节点生命周期、文件落盘等丰富语义折叠进 {@code token} 事件的 {@code kind} 字段，由前端分发。</p>
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final CodeGenPipeline pipeline;
    private final IterationPipeline iterationPipeline;
    private final AgentFactory agentFactory;
    private final ProjectService projectService;
    private final GenerationTaskService taskService;
    private final ChatMessageMapper chatMessageMapper;
    private final GenerationStreamRegistry streamRegistry;
    private final PromptTemplateLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

    public GenerationService(CodeGenPipeline pipeline,
            IterationPipeline iterationPipeline,
            AgentFactory agentFactory,
            ProjectService projectService,
            GenerationTaskService taskService,
            ChatMessageMapper chatMessageMapper,
            GenerationStreamRegistry streamRegistry,
            PromptTemplateLoader promptLoader,
            ObjectMapper objectMapper,
            @Qualifier(AsyncConfig.GENERATION_EXECUTOR) Executor executor) {
        this.pipeline = pipeline;
        this.iterationPipeline = iterationPipeline;
        this.agentFactory = agentFactory;
        this.projectService = projectService;
        this.taskService = taskService;
        this.chatMessageMapper = chatMessageMapper;
        this.streamRegistry = streamRegistry;
        this.promptLoader = promptLoader;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    /**
     * 创建首次生成任务。
     *
     * @param projectId 项目 ID
     * @param prompt    用户需求
     * @return 任务 ID
     */
    public String createGeneration(Long projectId, String prompt) {
        ensureProjectExists(projectId);
        String taskId = generateTaskId();
        taskService.createTask(projectId, taskId, "create", prompt);
        saveChatMessage(projectId, taskId, "user", prompt);
        log.info("创建生成任务: taskId={}, projectId={}", taskId, projectId);
        return taskId;
    }

    /**
     * 打开 SSE 流并异步执行生成流水线。
     *
     * <p>返回 Spring WebFlux 的 {@code Flux<ServerSentEvent<String>>}：在 Servlet (MVC) 模式下，
     * Spring 通过 {@code ReactiveTypeHandler} 把 {@code Flux} 适配为 Servlet 异步流式响应，
     * 逐条立即 flush，实现真正 token 级 SSE 推送。</p>
     *
     * @param taskId 任务 ID
     * @return 响应式 SSE 事件流
     */
    public Flux<ServerSentEvent<String>> streamGeneration(String taskId) {
        var task = taskService.getByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return buildStreamFlux(taskId, task.getProjectId(), task.getPrompt(), this::runPipeline);
    }

    /**
     * 打开迭代修改的 SSE 流并执行。
     *
     * @param taskId 任务 ID
     * @return 响应式 SSE 事件流
     */
    public Flux<ServerSentEvent<String>> streamIteration(String taskId) {
        var task = taskService.getByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return buildStreamFlux(taskId, task.getProjectId(), task.getPrompt(), this::runIteration);
    }

    /**
     * 统一构建 SSE Flux：绑定取消信号、注册 emitter，并在独立线程驱动流水线，
     * 把高层流水线事件经 {@link FluxStreamEmitter} 推送为统一封装的 {@link ServerSentEvent}。
     *
     * <p>取消/断连时通过 {@code sink.onCancel} 标记停止，使正在执行的流式节点提前退出；
     * {@code sink.onDispose} 负责注销 emitter 与上下文。流水线线程结束后 {@code sink.complete()}
     * 正常结束响应式流。</p>
     */
    private Flux<ServerSentEvent<String>> buildStreamFlux(String taskId, Long projectId, String prompt,
            PipelineRunner runner) {
        return Flux.create(sink -> {
            StreamContext context = new StreamContext(taskId);
            streamContextMap.put(taskId, context);
            sink.onCancel(() -> stopStreamProcessing(taskId));
            sink.onDispose(() -> cleanup(taskId));

            FluxStreamEmitter emitter = new FluxStreamEmitter(taskId, sink, objectMapper);
            streamRegistry.register(taskId, emitter);

            executor.execute(() -> {
                try {
                    runner.run(taskId, projectId, prompt, emitter, context);
                } finally {
                    GenerationContext.clear();
                    sink.complete();
                }
            });
        });
        // 备注：SSE 心跳保活留给后续接入；当前生成/迭代过程 Token 持续产出，连接不会空闲。
    }

    /**
     * 停止生成。
     *
     * <p>同时向 {@link GenerationStreamRegistry} 注册停止请求，使正在执行的节点
     * 能在流式回调中检测到停止信号并提前退出。</p>
     *
     * @param taskId 任务 ID
     */
    public void stopGeneration(String taskId) {
        streamRegistry.requestStop(taskId);
        stopStreamProcessing(taskId);
        taskService.markStopped(taskId);
    }

    /**
     * 创建迭代修改任务并直接执行（迭代走短流程，由 IterationAgent 驱动）。
     *
     * @param projectId 项目 ID
     * @param prompt    迭代修改指令
     * @return 任务 ID
     */
    public String iterate(Long projectId, String prompt) {
        ensureProjectExists(projectId);
        String taskId = generateTaskId();
        taskService.createTask(projectId, taskId, "iterate", prompt);
        saveChatMessage(projectId, taskId, "user", prompt);
        return taskId;
    }

    /**
     * 流水线执行体契约，供 generation / iteration 两条路径复用 {@link #buildStreamFlux}。
     */
    @FunctionalInterface
    public interface PipelineRunner {
        void run(String taskId, Long projectId, String prompt, FluxStreamEmitter emitter, StreamContext context);
    }

    private void runPipeline(String taskId, Long projectId, String prompt,
            FluxStreamEmitter emitter, StreamContext context) {
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(CodeGenState.PROMPT, prompt);
            inputs.put(CodeGenState.PROJECT_ID, String.valueOf(projectId));
            inputs.put(CodeGenState.TASK_ID, taskId);

            NodeOutput<CodeGenState> last = null;
            taskService.updateStage(taskId, "requirement_analysis");
            for (NodeOutput<CodeGenState> output :
                    pipeline.getCompiledGraph().stream(inputs)) {
                if (context.stopped) {
                    break;
                }
                last = output;
            }
            if (context.stopped) {
                return;
            }
            if (last != null) {
                var finalState = last.state();
                String previewUrl = finalState.previewUrl().orElse(null);
                Integer port = finalState.previewPort().orElse(0);
                Integer buildTime = finalState.buildTime().orElse(0);
                taskService.markCompleted(taskId, previewUrl, buildTime);
                emitter.complete(previewUrl, port, buildTime);
            }
        } catch (Exception e) {
            log.error("[{}] 流水线执行失败", taskId, e);
            taskService.markFailed(taskId, e.getMessage());
            emitter.error("生成失败: " + e.getMessage());
        }
    }

    private void runIteration(String taskId, Long projectId, String prompt,
            FluxStreamEmitter emitter, StreamContext context) {
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(CodeGenState.PROJECT_ID, String.valueOf(projectId));
            inputs.put(CodeGenState.TASK_ID, taskId);
            inputs.put(CodeGenState.ITERATION_PROMPT, prompt);

            NodeOutput<CodeGenState> last = null;
            taskService.updateStage(taskId, "iteration_intent_analysis");
            for (NodeOutput<CodeGenState> output :
                    iterationPipeline.getCompiledGraph().stream(inputs)) {
                if (context.stopped) {
                    break;
                }
                last = output;
            }
            if (context.stopped) {
                return;
            }
            if (last != null) {
                var finalState = last.state();
                String previewUrl = finalState.previewUrl().orElse(null);
                Integer port = finalState.previewPort().orElse(0);
                Integer buildTime = finalState.buildTime().orElse(0);
                taskService.markCompleted(taskId, previewUrl, buildTime);
                emitter.complete(previewUrl, port, buildTime);
            }
        } catch (Exception e) {
            log.error("[{}] 迭代修改失败", taskId, e);
            taskService.markFailed(taskId, e.getMessage());
            emitter.error("迭代修改失败: " + e.getMessage());
        }
    }

    private void stopStreamProcessing(String taskId) {
        StreamContext context = streamContextMap.get(taskId);
        if (context != null) {
            context.stopped = true;
        }
    }

    private void cleanup(String taskId) {
        streamContextMap.remove(taskId);
        streamRegistry.unregister(taskId);  // 同时清理 stoppedTasks
    }

    private void ensureProjectExists(Long projectId) {
        if (projectService.getById(projectId) == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }
    }

    private void saveChatMessage(Long projectId, String taskId, String role, String content) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setProjectId(projectId);
        message.setTaskId(taskId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
    }

    private String generateTaskId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 销毁时清理所有流上下文。
     */
    @PreDestroy
    public void destroy() {
        streamContextMap.keySet().forEach(this::stopStreamProcessing);
        streamContextMap.clear();
    }

    /** 流上下文，记录停止标志。 */
    public static class StreamContext {
        final String taskId;
        volatile boolean stopped;

        StreamContext(String taskId) {
            this.taskId = taskId;
        }
    }
}