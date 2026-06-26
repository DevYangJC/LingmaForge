package com.lingmaforge.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * 创建后端所需的 LangChain4j 基础设施 Bean。
 *
 * <p>当前接入单个兼容 OpenAI 协议的 ChatModel（默认 DashScope 兼容模式）。
 * 多模型路由（DeepSeek + Claude）可在本类扩展为多个带 @Qualifier 的 Bean。</p>
 */
@Configuration
@EnableConfigurationProperties({LingmaAiProperties.class, LingmaSandboxProperties.class})
public class LangChain4jConfig {

    /**
     * 创建兼容 OpenAI 协议的 LangChain4j 聊天模型。
     *
     * @param properties AI 配置项
     * @return ChatModel 实例
     */
    @Bean
    public ChatModel langChain4jChatModel(LingmaAiProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .logRequests(properties.logRequests())
                .logResponses(properties.logResponses())
                .maxRetries(properties.maxRetries())
                .build();
    }
}
