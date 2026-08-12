package com.lingmaforge.backend.workbench.ai.dialog;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.service.ChatReplyAgent;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;

/**
 * 闲聊回复节点。
 *
 * <p>Phase 2 改造为真实流式回复节点。在 {@link #execute(DialogState)} 内同步阻塞消费
 * {@link ChatReplyAgent#reply(String)} 返回的 {@code TokenStream}：逐 token 经
 * {@link GenerationStreamEmitter#emitChatToken(String)} 推 SSE，流结束后推送
 * {@link GenerationStreamEmitter#emitChatComplete(String)} 完成事件，完整回复写入
 * {@link DialogState#DELEGATE_RESULT} 供后续落库。</p>
 *
 * <p><b>emitter 获取方式</b>：本节点由 {@code DialogRouter} 以 {@code node_async}
 * 派发到 fork-join 线程，{@code GenerationContext} 的 ThreadLocal 不会传播。
 * 因此通过 {@link GenerationStreamRegistry#get(String)} 按 {@code dialogId} 取 emitter，
 * 而非 ThreadLocal。{@code dialogId} 由 {@link DialogState#DIALOG_ID} 提供。</p>
 *
 * <p><b>停止控制</b>：每个 token 回调检查
 * {@link GenerationStreamRegistry#isStopRequested(String)}，若已请求停止则提前完成 future，
 * 不再消费后续 token。</p>
 *
 * <p><b>异常兜底</b>：{@code TokenStream.start()} 可能以两种方式失败——
 * <ul>
 *   <li>立即同步抛异常（如 {@code NoOpStreamingModel.chat()} 在 start() 内直接 throw
 *       {@code IllegalStateException}，不会触发 {@code onError} 回调）</li>
 *   <li>通过 {@code onError} 回调异步完成 future 异常，随后 {@code future.join()} 抛
 *       {@code CompletionException}</li>
 * </ul>
 * 故整个 {@code reply(...).start() + future.join()} 必须包在同一个 try-catch 内，
 * 任意异常都走 {@code emitter.error()} + 返回 {@code "[闲聊回复失败]"} 占位，
 * 保证图不因闲聊节点崩溃而中断。</p>
 */
@Component
public class ChatReplyNode {

    private static final Logger log = LoggerFactory.getLogger(ChatReplyNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "chat_reply";

    private final ChatReplyAgent chatReplyAgent;
    private final GenerationStreamRegistry streamRegistry;

    /**
     * 构造闲聊回复节点。
     *
     * @param agentFactory  Agent 工厂，用于创建 {@link ChatReplyAgent}
     * @param streamRegistry 流注册表，用于按 dialogId 获取 emitter 与停止标志
     */
    public ChatReplyNode(AgentFactory agentFactory,
            GenerationStreamRegistry streamRegistry) {
        this.chatReplyAgent = agentFactory.createChatReplyAgent();
        this.streamRegistry = streamRegistry;
    }

    /**
     * 执行闲聊回复：流式消费 TokenStream，逐 token 推 SSE，完整回复写入 DELEGATE_RESULT。
     *
     * <p>整个方法对图引擎仍是同步的——{@code CompletableFuture.join()} 阻塞至流结束。
     * 流式 token 推送与 SSE 完成事件在节点内完成，调用方（{@code ChatService}）不再重复推送。</p>
     *
     * @param state 对话状态，须含 {@link DialogState#DIALOG_ID} 与 {@link DialogState#USER_MESSAGE}
     * @return 状态更新，携带完整回复文本（{@link DialogState#DELEGATE_RESULT}）
     */
    public Map<String, Object> execute(DialogState state) {
        String userMessage = state.userMessage().orElse("");
        String dialogId = state.dialogId().orElse("");
        GenerationStreamEmitter emitter = streamRegistry.get(dialogId);

        if (emitter == null) {
            // 无 SSE 连接（如单元测试 / 非流式调用）——回退为非流式占位
            log.warn("[chat_reply] 未找到 dialogId={} 的 emitter，回退非流式", dialogId);
            return Map.of(DialogState.DELEGATE_RESULT,
                    "[闲聊回复失败：SSE 连接未建立]");
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();
        // 停止标志：onPartialResponse 检测到停止请求后置 true，阻止 onCompleteResponse 重复 complete
        final boolean[] stopped = {false};

        try {
            // .start() 同步触发模型调用；NoOpStreamingModel 等"立即失败"的模型会在
            // start() 内同步抛异常（不会走 onError 回调），故必须包在 try 内统一兜底。
            chatReplyAgent.reply(userMessage)
                    .onPartialResponse(token -> {
                        if (streamRegistry.isStopRequested(dialogId)) {
                            if (!stopped[0]) {
                                stopped[0] = true;
                                future.complete(responseBuilder.toString());
                            }
                            return;
                        }
                        emitter.emitChatToken(token);
                        responseBuilder.append(token);
                    })
                    .onPartialThinking((PartialThinking thinking) -> {
                        // Phase 2 暂不推送思考过程到闲聊通道，留给后续迭代
                    })
                    .onCompleteResponse((ChatResponse chatResponse) -> {
                        if (!stopped[0] && !future.isDone()) {
                            future.complete(responseBuilder.toString());
                        }
                    })
                    .onError(error -> {
                        if (!stopped[0] && !future.isDone()) {
                            future.completeExceptionally(error);
                        }
                    })
                    .start();

            String fullResponse = future.join();
            emitter.emitChatComplete(fullResponse);
            log.info("[chat_reply] 流式回复完成: dialogId={}, length={}",
                    dialogId, fullResponse.length());
            return Map.of(DialogState.DELEGATE_RESULT, fullResponse);
        } catch (Exception e) {
            // 兜底两类异常：
            //  1) start() 内同步抛出（如 NoOpStreamingModel.chat() 抛 IllegalStateException）
            //  2) future.completeExceptionally 后 join() 抛出的 CompletionException
            log.error("[chat_reply] 流式回复失败: dialogId={}", dialogId, e);
            emitter.error("闲聊回复失败: " + e.getMessage());
            return Map.of(DialogState.DELEGATE_RESULT, "[闲聊回复失败]");
        }
    }
}
