package com.lingmaforge.backend.workbench.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bsc.langgraph4j.NodeOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.ResultCode;
import com.lingmaforge.backend.infra.config.AsyncConfig;
import com.lingmaforge.backend.workbench.ai.dialog.DialogIntent;
import com.lingmaforge.backend.workbench.ai.dialog.DialogRouter;
import com.lingmaforge.backend.workbench.ai.dialog.DialogState;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.observer.SseStreamEmitter;
import com.lingmaforge.backend.workbench.entity.ChatMessageEntity;
import com.lingmaforge.backend.workbench.entity.DialogEntity;
import com.lingmaforge.backend.workbench.mapper.ChatMessageMapper;
import com.lingmaforge.backend.workbench.mapper.DialogMapper;

import jakarta.annotation.PreDestroy;

/**
 * 对话服务：编排 {@link DialogRouter} 对话入口图并通过 SSE 流式推送闲聊回复。
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建 / 查询对话会话（{@link DialogEntity}）</li>
 *   <li>发送消息并打开 SSE 流：异步驱动 {@link DialogRouter#getCompiledGraph()}，
 *       逐节点消费 {@link NodeOutput}</li>
 *   <li>chat_reply 节点在 execute 内部自行推送 token 与 complete 事件，
 *       本服务负责落库 assistant 消息与非 chat 意图的兜底事件</li>
 *   <li>查询历史消息、停止回复</li>
 * </ul>
 * 线程模型仿 {@link GenerationService}：SseEmitter(0L) → SseStreamEmitter 包裹 →
 * 注册到 streamRegistry → 心跳 → 派发到 generationExecutor → 驱动图执行。</p>
 *
 * <p><b>emitter 获取方式</b>：DialogRouter 用 {@code node_async} 把节点派发到 fork-join
 * 线程，{@code GenerationContext} 的 ThreadLocal 不会传播。因此以 {@code dialogId} 为 key
 * 注册 emitter 到 {@link GenerationStreamRegistry}，{@code ChatReplyNode} 按
 * {@code dialogId} 取 emitter。</p>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** SSE 连接超时时间：不限时（依赖网关管理真实超时）。 */
    private static final long SSE_TIMEOUT = 0L;

    private final DialogRouter dialogRouter;
    private final DialogMapper dialogMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final GenerationStreamRegistry streamRegistry;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    /** 每个对话 SSE 流的上下文（停止标志 + 心跳 future）。 */
    private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-heartbeat-thread");
        thread.setDaemon(true);
        return thread;
    });

    public ChatService(DialogRouter dialogRouter,
            DialogMapper dialogMapper,
            ChatMessageMapper chatMessageMapper,
            GenerationStreamRegistry streamRegistry,
            ObjectMapper objectMapper,
            @Qualifier(AsyncConfig.GENERATION_EXECUTOR) Executor executor) {
        this.dialogRouter = dialogRouter;
        this.dialogMapper = dialogMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.streamRegistry = streamRegistry;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    /**
     * 创建对话会话。
     *
     * @param projectId 关联项目 ID，可空（闲聊无项目）
     * @param title     会话标题，可空（默认 "新对话"）
     * @return 业务会话 ID（= SSE streamId）
     */
    public String createDialog(Long projectId, String title) {
        String dialogId = generateDialogId();
        DialogEntity dialog = new DialogEntity();
        dialog.setDialogId(dialogId);
        dialog.setProjectId(projectId);
        dialog.setTitle(title != null && !title.isBlank() ? title : "新对话");
        dialog.setStatus("active");
        dialogMapper.insert(dialog);
        log.info("创建对话会话: dialogId={}, projectId={}, title={}", dialogId, projectId, dialog.getTitle());
        return dialogId;
    }

    /**
     * 发送消息并打开 SSE 流：落库用户消息 → 建立 SSE → 异步驱动 DialogRouter。
     *
     * <p>chat_reply 节点在 execute 内部逐 token 推 {@code message} 事件并在结束时推
     * complete 标志；本方法在图执行完毕后落库 assistant 回复，并 safeComplete 关闭 SSE。</p>
     *
     * @param dialogId    会话 ID
     * @param userMessage 用户消息
     * @return SseEmitter
     */
    public SseEmitter sendMessage(String dialogId, String userMessage) {
        DialogEntity dialog = dialogMapper.selectOne(
                new LambdaQueryWrapper<DialogEntity>()
                        .eq(DialogEntity::getDialogId, dialogId));
        if (dialog == null) {
            throw new BusinessException(ResultCode.DIALOG_NOT_FOUND);
        }

        // 落库用户消息
        saveChatMessage(dialog.getProjectId(), dialogId, "user", userMessage);

        // 建立 SSE
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        SseStreamEmitter sseEmitter = new SseStreamEmitter(dialogId, emitter, objectMapper);
        StreamContext context = new StreamContext(dialogId, sseEmitter);
        streamContextMap.put(dialogId, context);
        streamRegistry.register(dialogId, sseEmitter);

        emitter.onCompletion(() -> cleanup(dialogId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: dialogId={}", dialogId);
            stopStreamProcessing(dialogId);
        });
        emitter.onError(throwable -> {
            log.warn("SSE 连接异常: dialogId={}", dialogId, throwable);
            stopStreamProcessing(dialogId);
        });

        // 心跳定时器
        ScheduledFuture<?> heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                log.debug("SSE 发送心跳失败: dialogId={}", dialogId);
            }
        }, 15, 15, TimeUnit.SECONDS);
        context.heartbeatFuture = heartbeatFuture;

        Long projectId = dialog.getProjectId();
        executor.execute(() -> runDialog(dialogId, projectId, userMessage, sseEmitter, context));
        return emitter;
    }

    /**
     * 停止回复。
     *
     * @param dialogId 会话 ID
     */
    public void stopDialog(String dialogId) {
        streamRegistry.requestStop(dialogId);
        stopStreamProcessing(dialogId);
    }

    /**
     * 查询会话历史消息（按时间升序）。
     *
     * @param dialogId 会话 ID
     * @return 消息列表
     */
    public List<ChatMessageEntity> getMessages(String dialogId) {
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getDialogId, dialogId)
                        .orderByAsc(ChatMessageEntity::getCreatedAt));
    }

    /**
     * 查询会话列表，可按项目过滤。
     *
     * @param projectId 项目 ID，可空（查全部）
     * @return 会话列表
     */
    public List<DialogEntity> listDialogs(Long projectId) {
        LambdaQueryWrapper<DialogEntity> wrapper = new LambdaQueryWrapper<DialogEntity>()
                .orderByDesc(DialogEntity::getCreatedAt);
        if (projectId != null) {
            wrapper.eq(DialogEntity::getProjectId, projectId);
        }
        return dialogMapper.selectList(wrapper);
    }

    /**
     * 驱动 DialogRouter 图执行。
     *
     * <p>chat_reply 节点在 execute 内部已推送 token 与 complete 事件，本方法不再重复推送。
     * 图执行完毕后落库 assistant 回复。非 chat 意图（Phase 2 仍为桩）推一个 complete 兜底。</p>
     */
    private void runDialog(String dialogId, Long projectId, String userMessage,
            SseStreamEmitter emitter, StreamContext context) {
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(DialogState.DIALOG_ID, dialogId);
            inputs.put(DialogState.USER_MESSAGE, userMessage);
            if (projectId != null) {
                inputs.put(DialogState.PROJECT_ID, String.valueOf(projectId));
            }

            NodeOutput<DialogState> last = null;
            for (NodeOutput<DialogState> output : dialogRouter.getCompiledGraph().stream(inputs)) {
                if (context.stopped) {
                    break;
                }
                last = output;
            }
            if (context.stopped) {
                return;
            }

            if (last != null) {
                String reply = last.state().delegateResult().orElse("");
                DialogIntent intent = last.state().intent().orElse(DialogIntent.CHAT);
                // 落库 assistant 回复
                if (reply != null && !reply.isBlank()) {
                    saveChatMessage(projectId, dialogId, "assistant", reply);
                }
                // 非 chat 意图（桩节点）：推一个 complete 兜底，避免前端无限等待
                if (intent != DialogIntent.CHAT) {
                    emitter.complete("", 0, 0);
                }
                // chat 意图的 complete 事件已由 ChatReplyNode.emitChatComplete 推送，这里不重复
            }
        } catch (Exception e) {
            log.error("[{}] 对话处理失败", dialogId, e);
            emitter.error("对话处理失败: " + e.getMessage());
        } finally {
            emitter.safeComplete();
            cleanup(dialogId);
        }
    }

    private void stopStreamProcessing(String dialogId) {
        StreamContext context = streamContextMap.get(dialogId);
        if (context != null) {
            context.stopped = true;
        }
    }

    private void cleanup(String dialogId) {
        StreamContext context = streamContextMap.remove(dialogId);
        if (context != null && context.heartbeatFuture != null) {
            context.heartbeatFuture.cancel(true);
        }
        streamRegistry.unregister(dialogId);
    }

    private void saveChatMessage(Long projectId, String dialogId, String role, String content) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setProjectId(projectId);
        message.setDialogId(dialogId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
    }

    private String generateDialogId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 销毁时清理所有流上下文。
     */
    @PreDestroy
    public void destroy() {
        streamContextMap.keySet().forEach(this::stopStreamProcessing);
        streamContextMap.clear();
        heartbeatExecutor.shutdown();
    }

    /** 流上下文，记录停止标志与心跳 future。 */
    private static class StreamContext {
        final String dialogId;
        final SseStreamEmitter emitter;
        volatile boolean stopped;
        ScheduledFuture<?> heartbeatFuture;

        StreamContext(String dialogId, SseStreamEmitter emitter) {
            this.dialogId = dialogId;
            this.emitter = emitter;
        }
    }
}
