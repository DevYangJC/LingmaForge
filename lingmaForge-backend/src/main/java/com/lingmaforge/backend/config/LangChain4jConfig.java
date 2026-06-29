package com.lingmaforge.backend.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.lingmaforge.backend.config.LingmaModelsProperties.ModelConfig;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * LangChain4j 多模型 Bean 配置。
 *
 * <p>根据 {@code application.yml} 中 {@code lingma.models} 的配置，动态创建多个 {@link ChatModel} Bean。
 * 每个模型对应 Map 中的一个 Entry，key 为模型别名（如 {@code deepseek}、{@code claude}、{@code qwen}）。</p>
 *
 * <p><b>支持的 provider 类型</b>：</p>
 * <ul>
 *   <li>{@code openai} —— 通过 {@link OpenAiChatModel} 接入（DeepSeek / 通义千问 / GPT-4 均兼容此协议）</li>
 *   <li>{@code anthropic} —— 通过 {@link AnthropicChatModel} 接入（Claude Sonnet 4 / Claude Opus 4）</li>
 * </ul>
 *
 * <p>{@code AgentFactory} 通过注入 {@code Map<String, ChatModel>} 并根据 {@code lingma.agents.*.model} 配置
 * 为每个 Agent 分配合适的模型，实现一行配置切换模型、按阶段控制成本。</p>
 */
@Configuration
@EnableConfigurationProperties({ LingmaModelsProperties.class, LingmaSandboxProperties.class })
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    /**
     * 创建命名模型 Map。
     *
     * <p>每个配置的模型独立创建一个 {@link ChatModel} 实例。Agent 通过 key 索引取用，
     * 配合 {@code lingma.agents.*.model} 实现"哪个阶段用哪个模型"的路由。</p>
     *
     * @param properties 多模型配置
     * @return 模型别名 → ChatModel 实例的映射
     */
    @Bean
    @Primary
    public Map<String, ChatModel> chatModels(LingmaModelsProperties properties) {
        Map<String, ModelConfig> modelConfigs = properties.models();
        if (modelConfigs == null || modelConfigs.isEmpty()) {
            log.warn("未配置任何 AI 模型（lingma.models 为空），Agent 将无法工作！"
                    + "请在 application.yml 中至少配置一个模型。");
            return Collections.emptyMap();
        }

        Map<String, ChatModel> models = new HashMap<>();
        modelConfigs.forEach((name, config) -> {
            ChatModel model = buildModel(name, config);
            if (model != null) {
                models.put(name, model);
            }
        });

        if (models.isEmpty()) {
            log.warn("所有模型均因缺少 api-key 被跳过，Agent 将无法工作。"
                    + "请设置环境变量（如 DEEPSEEK_API_KEY、ANTHROPIC_API_KEY）。");
        }
        return models;
    }

    private ChatModel buildModel(String name, ModelConfig config) {
        String apiKey = config.apiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("$")) {
            log.info("模型 [{}] 的 api-key 未设置或仍为占位符，跳过创建", name);
            return null;
        }

        String provider = config.provider() == null ? "openai" : config.provider().toLowerCase();
        boolean logReq = config.logRequests() != null && config.logRequests();
        boolean logResp = config.logResponses() != null && config.logResponses();
        int retries = config.maxRetries() != null ? config.maxRetries() : 2;

        return switch (provider) {
            case "anthropic" -> {
                log.info("创建 Anthropic 模型 [{}]: model={}", name, config.modelName());
                yield AnthropicChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(config.modelName())
                        .logRequests(logReq)
                        .logResponses(logResp)
                        .maxRetries(retries)
                        .build();
            }
            case "openai" -> {
                String baseUrl = config.baseUrl();
                log.info("创建 OpenAI 兼容模型 [{}]: baseUrl={}, model={}", name, baseUrl, config.modelName());
                yield OpenAiChatModel.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .modelName(config.modelName())
                        .logRequests(logReq)
                        .logResponses(logResp)
                        .maxRetries(retries)
                        .build();
            }
            default -> {
                log.warn("模型 [{}] 的 provider [{}] 不支持，跳过创建。支持的类型: openai, anthropic", name, provider);
                yield null;
            }
        };
    }
}
