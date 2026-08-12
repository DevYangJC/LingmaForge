package com.lingmaforge.backend.workbench.ai.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

/**
 * 对话入口层的状态定义。
 *
 * <p>继承 LangGraph4j 的 {@link AgentState}，作为 {@link DialogRouter} 各节点共享的"黑板"。
 * 仿照 {@code CodeGenState} 的 Channel 声明模式：</p>
 * <ul>
 *   <li>{@link Channels#base(java.util.function.Supplier)} —— LastValue 覆盖合并，用于单值字段</li>
 *   <li>{@link Channels#appender(java.util.function.Supplier)} —— 追加合并，用于列表字段</li>
 * </ul>
 *
 * <p>Phase 1 只用到 {@link #USER_MESSAGE} → {@link #INTENT} 路径；
 * {@link #MESSAGES} / {@link #DIALOG_ID} 等字段为后续 Phase 预留。</p>
 */
public class DialogState extends AgentState {

    /** 会话 ID（= SSE streamId）。Phase 2 接口层使用，Phase 1 预留。 */
    public static final String DIALOG_ID = "dialogId";
    /**
     * 关联项目 ID（可空，闲聊无项目）。
     *
     * <p><b>类型注记</b>：以 {@code String} 形式存储，与 {@code CodeGenState.PROJECT_ID}
     * 对齐（跨图桥接时避免类型转换）；落库时由 Service 层转 {@code Long}。</p>
     */
    public static final String PROJECT_ID = "projectId";
    /** 当前轮用户原始消息。 */
    public static final String USER_MESSAGE = "userMessage";
    /** 对话历史（追加合并）。Phase 2 多轮上下文使用。 */
    public static final String MESSAGES = "messages";
    /** 识别出的意图。 */
    public static final String INTENT = "intent";
    /** 意图置信度。 */
    public static final String INTENT_CONFIDENCE = "intentConfidence";
    /** delegate 节点的返回结果（Phase 1 桩节点写入占位文本）。 */
    public static final String DELEGATE_RESULT = "delegateResult";

    public DialogState(Map<String, Object> data) {
        super(data);
    }

    private static <T> Channel<T> nullableChannel() {
        return Channels.base((java.util.function.Supplier<T>) null);
    }

    /**
     * 声明所有状态字段的 Channel 定义。
     *
     * @return 字段名到 Channel 的映射
     */
    public static Map<String, Channel<?>> channels() {
        return Map.ofEntries(
                Map.entry(DIALOG_ID, nullableChannel()),
                Map.entry(PROJECT_ID, nullableChannel()),
                Map.entry(USER_MESSAGE, nullableChannel()),
                // 对话历史：追加合并，每轮追加 user/assistant 消息
                Map.entry(MESSAGES, Channels.appender(ArrayList::new)),
                Map.entry(INTENT, nullableChannel()),
                Map.entry(INTENT_CONFIDENCE, nullableChannel()),
                Map.entry(DELEGATE_RESULT, nullableChannel()));
    }

    /** 读取会话 ID。 */
    public Optional<String> dialogId() {
        return value(DIALOG_ID);
    }

    /** 读取关联项目 ID。 */
    public Optional<String> projectId() {
        return value(PROJECT_ID);
    }

    /** 读取当前轮用户原始消息。 */
    public Optional<String> userMessage() {
        return value(USER_MESSAGE);
    }

    /** 读取对话历史。 */
    public Optional<List<DialogMessage>> messages() {
        return value(MESSAGES);
    }

    /** 读取识别出的意图。 */
    public Optional<DialogIntent> intent() {
        return value(INTENT);
    }

    /** 读取意图置信度。 */
    public Optional<Double> intentConfidence() {
        return value(INTENT_CONFIDENCE);
    }

    /** 读取 delegate 节点的返回结果。 */
    public Optional<String> delegateResult() {
        return value(DELEGATE_RESULT);
    }
}
