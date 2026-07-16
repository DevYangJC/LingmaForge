package com.lingmaforge.backend.workbench.ai.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 内存记忆存储——纯 {@link ConcurrentHashMap} 实现的 {@link ChatMemoryStore}。
 *
 * <p>当前为内存模式（lingmaForge-dev 无 Redis 依赖），keyed by memoryId（{@code projectId + "_" + codeGenType}）。
 * 后续可无缝替换为 {@code RedisChatMemoryStore}（langchain4j-community-redis）或数据库持久化实现。</p>
 *
 * <p>LangChain4j 的 {@code MessageWindowChatMemory} 通过本接口管理滑动窗口：
 * {@link #getMessages(Object)} 取全量 → Memory 裁剪到 maxMessages 窗口 →
 * {@link #updateMessages(Object, List)} 写回裁剪后的列表。</p>
 */
public class InMemoryChatMemoryStore implements ChatMemoryStore {

    private final Map<String, List<ChatMessage>> store = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> messages = store.get(memoryId.toString());
        return messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        store.put(memoryId.toString(), new ArrayList<>(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        store.remove(memoryId.toString());
    }

    /**
     * 清空所有记忆（通常用于测试或服务关闭时）。
     */
    public void clearAll() {
        store.clear();
    }

    /**
     * 按前缀删除匹配的记忆（用于项目删除时级联清理）。
     *
     * @param prefix 键前缀，如 "123_vue-project" 或 "123_"
     */
    public void deleteByPrefix(String prefix) {
        store.keySet().removeIf(key -> key.startsWith(prefix));
    }
}