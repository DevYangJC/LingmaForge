package com.lingmaforge.backend.workbench.ai.memory;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 记忆消毒装饰器——修复/丢弃不合法的 ChatMessage。
 *
 * 当 AiMessage 既无 {@code text} 又无 {@code toolExecutionRequests} 时
 * （OpenAI 兼容 API 要求至少其一），若有 {@code thinking} 则用 thinking 填 text 修复；
 * 否则丢弃该消息，避免回放历史时 API 返回 400。</p>
 *
 * <p>包装在 {@link CompactingChatMemoryStore} 外面，确保压缩前的修复性读取不受脏数据影响。</p>
 */
public class SanitizingChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(SanitizingChatMemoryStore.class);

    private final ChatMemoryStore delegate;

    public SanitizingChatMemoryStore(ChatMemoryStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> messages = delegate.getMessages(memoryId);
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        return sanitize(messages);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        delegate.updateMessages(memoryId, messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        delegate.deleteMessages(memoryId);
    }

    private List<ChatMessage> sanitize(List<ChatMessage> messages) {
        List<ChatMessage> cleaned = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (!(msg instanceof AiMessage aiMsg)) {
                cleaned.add(msg);
                continue;
            }
            String text = aiMsg.text();
            boolean hasText = text != null && !text.isBlank();
            boolean hasTools = aiMsg.hasToolExecutionRequests();
            boolean hasThinking = !aiMsg.thinking().isEmpty();

            if (hasText || hasTools) {
                cleaned.add(msg);
            } else if (hasThinking) {
                String thinking = String.join("\n", aiMsg.thinking());
                cleaned.add(new AiMessage(thinking, aiMsg.toolExecutionRequests()));
                log.debug("修复 AiMessage：用 thinking 填充 text, memoryId={}", memoryId());
            }
            // 既无 text 也无 tools 也无 thinking → 丢弃
        }
        return cleaned;
    }

    private Object memoryId() {
        return "unknown"; // delegate doesn't expose memoryId; for debug logging only
    }
}