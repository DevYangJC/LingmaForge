package com.lingmaforge.backend.service;

import java.io.IOException;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.ai.factory.AgentFactory;
import com.lingmaforge.backend.ai.observer.GenerationContext;
import com.lingmaforge.backend.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.ai.pipeline.CodeGenPipeline;
import com.lingmaforge.backend.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.ResultCode;
import com.lingmaforge.backend.config.AsyncConfig;
import com.lingmaforge.backend.entity.ChatMessageEntity;
import com.lingmaforge.backend.mapper.ChatMessageMapper;

import jakarta.annotation.PreDestroy;

/**
 * 生成服务：编排 StateGraph 流水线并通过 SSE 流式推送进度。
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建生成任务，分配 taskId（= StateGraph threadId = SSE streamId）</li>
 *   <li>异步驱动 {@link CodeGenPipeline} 的 graph.stream()，遍历 NodeOutput</li>
 *   <li>把节点进度、文件生成、日志、完成/错误事件通过 SseEmitter 推送给前端</li>
 *   <li>支持停止生成、迭代修改</li>
 * </ul>
 * 每个任务对应一个 SseEmitter 与一个 GenerationStreamEmitter 实现。</p>
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    /** SSE 连接超时时间：5 分钟。 */
    private static final long SSE_TIMEOUT = 300_000L;

    private final CodeGenPipeline pipeline;
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
            AgentFactory agentFactory,
            ProjectService projectService,
            GenerationTaskService taskService,
            ChatMessageMapper chatMessageMapper,
            GenerationStreamRegistry streamRegistry,
            PromptTemplateLoader promptLoader,
            ObjectMapper objectMapper,
            @Qualifier(AsyncConfig.GENERATION_EXECUTOR) Executor executor) {
        this.pipeline = pipeline;
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
     * @param taskId 任务 ID
     * @return SseEmitter
     */
    public SseEmitter streamGeneration(String taskId) {
        var task = taskService.getByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        SseStreamEmitter sseEmitter = new SseStreamEmitter(taskId, emitter, objectMapper);
        StreamContext context = new StreamContext(taskId, sseEmitter);
        streamContextMap.put(taskId, context);
        streamRegistry.register(taskId, sseEmitter);

        emitter.onCompletion(() -> cleanup(taskId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: taskId={}", taskId);
            stopStreamProcessing(taskId);
        });
        emitter.onError(throwable -> {
            log.warn("SSE 连接异常: taskId={}", taskId, throwable);
            stopStreamProcessing(taskId);
        });

        Long projectId = task.getProjectId();
        String prompt = task.getPrompt();
        executor.execute(() -> runPipeline(taskId, projectId, prompt, sseEmitter, context));
        return emitter;
    }

    /**
     * 停止生成。
     *
     * @param taskId 任务 ID
     */
    public void stopGeneration(String taskId) {
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
     * 打开迭代修改的 SSE 流并执行。
     *
     * @param taskId 任务 ID
     * @return SseEmitter
     */
    public SseEmitter streamIteration(String taskId) {
        var task = taskService.getByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        SseStreamEmitter sseEmitter = new SseStreamEmitter(taskId, emitter, objectMapper);
        StreamContext context = new StreamContext(taskId, sseEmitter);
        streamContextMap.put(taskId, context);
        streamRegistry.register(taskId, sseEmitter);
        emitter.onCompletion(() -> cleanup(taskId));
        emitter.onTimeout(() -> stopStreamProcessing(taskId));
        emitter.onError(t -> stopStreamProcessing(taskId));

        Long projectId = task.getProjectId();
        String prompt = task.getPrompt();
        executor.execute(() -> runIteration(taskId, projectId, prompt, sseEmitter, context));
        return emitter;
    }

    private void runPipeline(String taskId, Long projectId, String prompt,
            SseStreamEmitter emitter, StreamContext context) {
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(CodeGenState.PROMPT, prompt);
            inputs.put(CodeGenState.PROJECT_ID, String.valueOf(projectId));
            inputs.put(CodeGenState.TASK_ID, taskId);

            NodeOutput<com.lingmaforge.backend.ai.pipeline.CodeGenState> last = null;
            taskService.updateStage(taskId, "requirement_analysis");
            for (NodeOutput<com.lingmaforge.backend.ai.pipeline.CodeGenState> output :
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
        } finally {
            GenerationContext.clear();
            emitter.safeComplete();
            cleanup(taskId);
        }
    }

    private void runIteration(String taskId, Long projectId, String prompt,
            SseStreamEmitter emitter, StreamContext context) {
        GenerationContext.set(projectId, taskId, emitter);
        try {
            taskService.updateStage(taskId, "iteration");
            emitter.emitNode("iteration", "正在理解修改意图并定位代码...", "TEXT");
            var agent = agentFactory.createIterationAgent();
            String contextSummary = buildIterationContext(projectId);
            String fullPrompt = "用户修改指令: " + prompt + "\n\n项目上下文:\n" + contextSummary;
            agent.modify(fullPrompt);
            if (!context.stopped) {
                var sandbox = new com.lingmaforge.backend.model.SandboxInfo(
                        projectService.getById(projectId).getSandboxUrl(), 0, "running");
                taskService.markCompleted(taskId, sandbox.url(), 0);
                emitter.complete(sandbox.url(), 0, 0);
            }
        } catch (Exception e) {
            log.error("[{}] 迭代修改失败", taskId, e);
            taskService.markFailed(taskId, e.getMessage());
            emitter.error("迭代修改失败: " + e.getMessage());
        } finally {
            GenerationContext.clear();
            emitter.safeComplete();
            cleanup(taskId);
        }
    }

    private String buildIterationContext(Long projectId) {
        var ctx = projectService.getProjectContext(projectId);
        return "框架: " + ctx.framework() + "\n文件列表:\n" + String.join("\n", ctx.filePaths());
    }

    private void stopStreamProcessing(String taskId) {
        StreamContext context = streamContextMap.get(taskId);
        if (context != null) {
            context.stopped = true;
        }
    }

    private void cleanup(String taskId) {
        streamContextMap.remove(taskId);
        streamRegistry.unregister(taskId);
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
    private static class StreamContext {
        final String taskId;
        final SseStreamEmitter emitter;
        volatile boolean stopped;

        StreamContext(String taskId, SseStreamEmitter emitter) {
            this.taskId = taskId;
            this.emitter = emitter;
        }
    }

    /**
     * 基于 SseEmitter 的 GenerationStreamEmitter 实现。
     */
    private static class SseStreamEmitter implements GenerationStreamEmitter {

        private final String taskId;
        private final SseEmitter emitter;
        private final ObjectMapper objectMapper;

        SseStreamEmitter(String taskId, SseEmitter emitter, ObjectMapper objectMapper) {
            this.taskId = taskId;
            this.emitter = emitter;
            this.objectMapper = objectMapper;
        }

        @Override
        public void emitNode(String nodeName, String text, String textType) {
            send("message", Map.of(
                    "threadId", taskId,
                    "nodeName", nodeName,
                    "text", text,
                    "textType", textType,
                    "error", false));
        }

        @Override
        public void emitFile(String path, String content, String status) {
            send("file", Map.of(
                    "threadId", taskId,
                    "path", path,
                    "content", content,
                    "status", status));
        }

        @Override
        public void emitLog(String text) {
            send("log", Map.of("threadId", taskId, "text", text));
        }

        @Override
        public void complete(String url, Integer port, Integer buildTime) {
            send("complete", Map.of(
                    "threadId", taskId,
                    "url", url == null ? "" : url,
                    "port", port == null ? 0 : port,
                    "buildTime", buildTime == null ? 0 : buildTime));
        }

        @Override
        public void error(String message) {
            send("error", Map.of(
                    "threadId", taskId,
                    "nodeName", "error",
                    "text", message,
                    "error", true));
        }

        private void send(String event, Object data) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event)
                        .data(objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 发送失败（连接可能已关闭）: taskId={}, event={}", taskId, event);
            }
        }

        void safeComplete() {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE complete 异常: taskId={}", taskId);
            }
        }
    }
}
