package com.lingmaforge.backend.workbench.ai.dialog;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 闲聊回复桩节点。
 *
 * <p>Phase 1 只返回占位状态，Phase 2 将在此直接调 {@code StreamingChatModel}，
 * 通过 {@code TokenStream} 逐 token 推 SSE。</p>
 *
 * <p><b>Phase 2 衔接注记</b>：接入 SSE 时需将本节点改造为流式——接收 SseEmitter
 * 与 taskId，用 {@code StreamingChatModel.stream(userMessage)} 返回的 {@code TokenStream}
 * 逐 token 写入 emitter，并在完成回调中 flush 落库。</p>
 */
@Component
public class ChatReplyNode {

    private static final Logger log = LoggerFactory.getLogger(ChatReplyNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "chat_reply";

    /**
     * 执行闲聊回复（桩实现）。
     *
     * @param state 对话状态
     * @return 占位状态更新
     */
    public Map<String, Object> execute(DialogState state) {
        log.info("[chat_reply] 桩节点执行 — 流式回复将在 Phase 2 实现");
        return Map.of(DialogState.DELEGATE_RESULT,
                "[桩] 已识别为闲聊意图，流式回复将在 Phase 2 实现");
    }
}
