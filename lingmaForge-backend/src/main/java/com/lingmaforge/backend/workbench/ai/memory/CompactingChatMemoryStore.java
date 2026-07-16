package com.lingmaforge.backend.workbench.ai.memory;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * Layer 1 微压缩：装饰 {@link ChatMemoryStore}，压缩旧的工具执行结果。
 *
 * <p>移植自 zero-code-monolith {@code CompactingChatMemoryStore}。策略：
 * <ul>
 *   <li>保留最近 {@link #KEEP_RECENT_RESULTS} 条 {@link ToolExecutionResultMessage} 的原始内容</li>
 *   <li>更早的工具结果（内容超过 {@link #MIN_COMPRESS_LENGTH} 字符）替换为 {@code [已执行: toolName]}</li>
 * </ul>
 *
 * <p>目的：减少发送给 LLM 的历史上下文中工具结果占用的 token，
 * 同时保留工具调用的痕迹，让模型知道"做过什么"但不浪费 token 在已处理过的详细内容上。</p>
 *
 * <p>对齐 zero-code：关键差异是 lingmaForge 用内存 store 替代 Redis delegate，
 * 其余设计（保留条数、最小压缩长度、占位符格式）与原版一致。</p>
 */
public class CompactingChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(CompactingChatMemoryStore.class);

    /** 保留最近多少条工具结果不压缩。对应约数轮工具交互。 */
    private static final int KEEP_RECENT_RESULTS = 8;

    /** 工具结果内容低于此长度不压缩（短结果压缩没有意义）。 */
    private static final int MIN_COMPRESS_LENGTH = 300;

    private final ChatMemoryStore delegate;

    public CompactingChatMemoryStore(ChatMemoryStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> messages = delegate.getMessages(memoryId);
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        return microCompact(messages);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        delegate.updateMessages(memoryId, messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        delegate.deleteMessages(memoryId);
    }

    /**
     * 调用 LLM 前执行持久微压缩：压缩旧工具结果并写回底层 store。
     *
     * @return true 表示本次实际写回了压缩后的消息
     */
    public boolean compactAndPersist(Object memoryId) {
        List<ChatMessage> messages = delegate.getMessages(memoryId);
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        MicroCompactResult result = microCompactWithStats(messages);
        if (result.compactedCount() <= 0) {
            return false;
        }
        delegate.updateMessages(memoryId, result.messages());
        log.debug("持久微压缩完成，memoryId={}, 压缩了 {} 条工具结果", memoryId, result.compactedCount());
        return true;
    }

    private List<ChatMessage> microCompact(List<ChatMessage> messages) {
        return microCompactWithStats(messages).messages();
    }

    private MicroCompactResult microCompactWithStats(List<ChatMessage> messages) {
        List<Integer> toolResultIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolExecutionResultMessage) {
                toolResultIndices.add(i);
            }
        }

        if (toolResultIndices.size() <= KEEP_RECENT_RESULTS) {
            return new MicroCompactResult(messages, 0);
        }

        int compactBoundary = toolResultIndices.size() - KEEP_RECENT_RESULTS;
        List<Integer> toCompactIndices = toolResultIndices.subList(0, compactBoundary);

        List<ChatMessage> compacted = new ArrayList<>(messages.size());
        int compactedCount = 0;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (toCompactIndices.contains(i) && msg instanceof ToolExecutionResultMessage toolResult) {
                String text = toolResult.text();
                if (text != null && text.length() > MIN_COMPRESS_LENGTH) {
                    String placeholder = "[已执行: " + toolResult.toolName() + "]";
                    compacted.add(ToolExecutionResultMessage.from(
                            toolResult.id(), toolResult.toolName(), placeholder));
                    compactedCount++;
                } else {
                    compacted.add(msg);
                }
            } else {
                compacted.add(msg);
            }
        }

        return new MicroCompactResult(compacted, compactedCount);
    }

    private record MicroCompactResult(List<ChatMessage> messages, int compactedCount) {
    }
}