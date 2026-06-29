package com.lingmaforge.backend.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 灵码工坊多模型与 Agent 路由配置。
 *
 * <p>将 {@code application.yml} 中的模型连接信息与 Agent 分配策略映射为 Java 对象。
 * 每个模型可独立配置 provider、api-key、model-name；每个 Agent 通过引用模型名实现路由。</p>
 *
 * <p><b>YAML 示例</b>：</p>
 * <pre>{@code
 * lingma:
 *   models:
 *     deepseek:
 *       base-url: https://api.deepseek.com/v1
 *       api-key: ${DEEPSEEK_API_KEY}
 *       model-name: deepseek-chat
 *       provider: openai
 *     claude:
 *       api-key: ${ANTHROPIC_API_KEY}
 *       model-name: claude-sonnet-4-20250514
 *       provider: anthropic
 *   agents:
 *     requirement-analysis:
 *       model: deepseek
 *     code-generation:
 *       model: claude
 * }</pre>
 */
@ConfigurationProperties(prefix = "lingma")
public record LingmaModelsProperties(
        /** 可用模型配置，key 为模型别名（如 deepseek、claude、qwen）。 */
        Map<String, ModelConfig> models,

        /** Agent 分配策略，key 为 Agent 名，value 指定使用哪个模型。 */
        Map<String, AgentModelConfig> agents) {

    /**
     * 单个模型的连接配置。
     *
     * @param baseUrl   API 端点（OpenAI 兼容协议必需；Anthropic 可省略）
     * @param apiKey    API 密钥
     * @param modelName 模型名称（如 deepseek-chat / claude-sonnet-4-20250514）
     * @param provider  协议类型：openai / anthropic
     * @param logRequests   是否打印请求日志，默认 false
     * @param logResponses  是否打印响应日志，默认 false
     * @param maxRetries    最大重试次数，默认 2
     */
    public record ModelConfig(
            String baseUrl,
            String apiKey,
            String modelName,
            String provider,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxRetries) {
    }

    /**
     * Agent 对模型的引用。
     *
     * @param model 使用的模型别名，必须对应 {@code lingma.models} 下的某个 key
     */
    public record AgentModelConfig(String model) {
    }
}
