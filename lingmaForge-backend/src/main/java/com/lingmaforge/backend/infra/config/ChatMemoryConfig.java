package com.lingmaforge.backend.infra.config;

import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lingmaforge.backend.workbench.ai.memory.CompactingChatMemoryStore;
import com.lingmaforge.backend.workbench.ai.memory.ContextCompactionService;
import com.lingmaforge.backend.workbench.ai.memory.InMemoryChatMemoryStore;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 对话记忆配置——装配零拷贝-装饰链：{@code InMemory → Compacting → MessageWindowChatMemory}。
 *
 * <p>内存模式（无 Redis），后续可替换 {@link InMemoryChatMemoryStore} 为
 * {@code RedisChatMemoryStore} 实现跨进程持久化。</p>
 *
 * <p>对外暴露：
 * <ul>
 *   <li>{@link #chatMemoryStore} —— 底层 store，供 {@link ContextCompactionService} 直接操作</li>
 *   <li>{@link #chatMemoryProvider} —— {@code Function<String, MessageWindowChatMemory>}，
 *       AgentFactory 用它为每个 memoryId 动态创建 160 窗口的 ChatMemory</li>
 *   <li>{@link #summaryChatModel} —— 用于摘要压缩的轻量模型，取自配置的第一个可用 {@link ChatModel}</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(ContextCompactionProperties.class)
public class ChatMemoryConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryConfig.class);

    /** Vue 项目迭代/生成保留的最近消息窗口大小——与 zero-code 一致。 */
    private static final int VUE_MAX_MESSAGES = 160;

    @Bean
    public InMemoryChatMemoryStore inMemoryStore() {
        return new InMemoryChatMemoryStore();
    }

    @Bean
    public CompactingChatMemoryStore compactingChatMemoryStore(InMemoryChatMemoryStore inMemoryStore) {
        return new CompactingChatMemoryStore(inMemoryStore);
    }

    /**
     * 底层 ChatMemoryStore Bean——供 {@link ContextCompactionService} 直接操作。
     * 实际类型为 {@link CompactingChatMemoryStore}（装饰了 {@link InMemoryChatMemoryStore}）。
     */
    @Bean
    public ChatMemoryStore chatMemoryStore(CompactingChatMemoryStore compactingStore) {
        return compactingStore;
    }

    /**
     * 记忆工厂：为每个 memoryId 创建独立的 {@link MessageWindowChatMemory(160)}。
     * AgentFactory 在每次创建 agent 时调用此函数动态装配记忆，确保多轮对话上下文连续。
     */
    @Bean
    public Function<String, MessageWindowChatMemory> chatMemoryProvider(
            CompactingChatMemoryStore compactingStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(compactingStore)
                .maxMessages(VUE_MAX_MESSAGES)
                .build();
    }

    /**
     * 摘要模型——用于 {@link ContextCompactionService} 自动压缩。
     * 取配置的第一个可用模型（通常为 deepseek-flash），降级可用其他模型。
     */
    @Bean
    @Qualifier("summaryChatModel")
    public ChatModel summaryChatModel(Map<String, ChatModel> chatModels) {
        if (chatModels != null && !chatModels.isEmpty()) {
            String key = chatModels.keySet().iterator().next();
            log.info("摘要模型选用: {}", key);
            return chatModels.get(key);
        }
        throw new IllegalStateException("没有可用的 ChatModel 用于摘要压缩，请配置至少一个模型");
    }
}