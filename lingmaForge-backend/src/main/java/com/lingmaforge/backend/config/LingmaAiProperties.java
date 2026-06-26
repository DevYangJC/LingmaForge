package com.lingmaforge.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用于创建 LangChain4j 聊天模型的配置项。
 */
@ConfigurationProperties(prefix = "lingma.ai")
public record LingmaAiProperties(
        String apiKey,
        String baseUrl,
        String modelName,
        boolean logRequests,
        boolean logResponses,
        Integer maxRetries) {
}