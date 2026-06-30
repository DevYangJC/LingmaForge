package com.lingmaforge.backend.workbench.ai.factory;

import java.util.Map;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import com.lingmaforge.backend.infra.config.LingmaModelsProperties.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.service.CodeGenAgent;
import com.lingmaforge.backend.workbench.ai.service.ExecutionPlanner;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;
import com.lingmaforge.backend.workbench.ai.service.RequirementAnalyzer;
import com.lingmaforge.backend.workbench.ai.service.StyleOptimizationAgent;
import com.lingmaforge.backend.workbench.ai.tool.FileTools;
import com.lingmaforge.backend.workbench.ai.tool.IterationTools;
import com.lingmaforge.backend.workbench.ai.tool.ProjectContextTools;
import com.lingmaforge.backend.infra.config.LingmaModelsProperties;
import com.lingmaforge.backend.infra.config.LingmaModelsProperties.AgentModelConfig;
import com.lingmaforge.backend.workbench.service.PromptTemplateLoader;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Agent 创建工厂。
 *
 * 核心设计——多模型路由：不同 Agent 注入不同的 {@link ChatModel} 实例，
 * 实现"便宜模型做分析、贵模型写代码"的分级成本控制。</p>
 *
 * 路由规则由 {@code application.yml} 的 {@code lingma.agents.*.model} 决定：</p>
 * {@code
 * lingma:
 *   agents:
 *     requirement-analysis:
 *       model: deepseek       ← 需求分析用便宜的 DeepSeek
 *     code-generation:
 *       model: claude         ← 代码生成用贵的 Claude
 * }
 *
 * <p>两大类创建模式：
 * <ul>
 *   <li><b>ai-service（无工具）</b>：需求分析、执行规划，单次调用返回结构化 Java 对象</li>
 *   <li><b>ai-service（带工具）</b>：代码生成、样式优化、迭代修改，内部驱动 Agent 循环</li>
 * </ul>
 * system prompt 由 {@link PromptTemplateLoader} 从 {@code resources/prompts} 加载。</p>
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    /** 单次文件生成内允许的最大工具调用往返数。 */
    private static final int MAX_TOOL_ROUND_TRIPS = 12;

    /** 模型别名 → ChatModel 实例的查找表。 */
    private final Map<String, ChatModel> chatModels;

    /** Agent 配置（哪个 Agent 用哪个模型）。 */
    private final Map<String, AgentModelConfig> agentConfigs;

    private final LingmaModelsProperties properties;
    private final PromptTemplateLoader promptLoader;
    private final FileTools fileTools;
    private final ProjectContextTools projectContextTools;
    private final IterationTools iterationTools;

    public AgentFactory(Map<String, ChatModel> chatModels,
            LingmaModelsProperties properties,
            PromptTemplateLoader promptLoader,
            FileTools fileTools,
            ProjectContextTools projectContextTools,
            IterationTools iterationTools) {
        this.chatModels = chatModels;
        this.properties = properties;
        this.agentConfigs = properties.agents() != null ? properties.agents() : Map.of();
        this.promptLoader = promptLoader;
        this.fileTools = fileTools;
        this.projectContextTools = projectContextTools;
        this.iterationTools = iterationTools;
    }

    // ======================== 模型解析 ========================

    /**
    /**
     * 根据 Agent 类型从配置中解析对应的 ChatModel。
     *
     * <p>查找链路：agentType.getType() → lingma.agents.{agentName}.model → lingma.models.{modelName} → ChatModel Bean。
     * 若配置缺失，回退到第一个可用模型。</p>
     *
     * @param agentType Agent 类型
     * @return 对应 ChatModel，永不返回 null
     * @throws IllegalStateException 当没有任何可用模型时
     */
    private ChatModel resolveModel(AgentType agentType) {
        String agentName = agentType.getType();
        AgentModelConfig agentConfig = agentConfigs.get(agentName);
        if (agentConfig != null && agentConfig.model() != null) {
            ChatModel model = chatModels.get(agentConfig.model());
            if (model != null) {
                log.info("Agent [{}] → 模型 [{}]", agentName, agentConfig.model());
                return model;
            }
            log.warn("Agent [{}] 配置的模型 [{}] 不可用（api-key 未设置？），回退到第一个可用模型",
                    agentName, agentConfig.model());
        }
        // 回退：取 Map 中第一个可用的模型
        if (!chatModels.isEmpty()) {
            String fallback = chatModels.keySet().iterator().next();
            log.warn("Agent [{}] 未配置或配置的模型不可用，使用第一个可用模型 [{}]", agentName, fallback);
            return chatModels.get(fallback);
        }
        // 没有任何可用模型：返回一个占位模型，让 Spring 上下文能正常加载，
        // 实际调用时会抛出明确错误提示用户配置 API Key
        log.error("❌ Agent [{}] 没有任何可用的 AI 模型！"
                + "请设置环境变量（如 DEEPSEEK_API_KEY、ANTHROPIC_API_KEY）并重启服务。", agentName);
        return new NoOpModel();
    }

    /**
     * 根据 Agent 类型从配置中解析对应的 StreamingChatModel。
     *
     * @param agentType Agent 类型
     * @return 对应 StreamingChatModel，永不返回 null
     */
    private StreamingChatModel resolveStreamingModel(AgentType agentType) {
        String agentName = agentType.getType();
        AgentModelConfig agentConfig = agentConfigs.get(agentName);
        if (agentConfig != null && agentConfig.model() != null) {
            String modelAlias = agentConfig.model();
            ModelConfig config = properties.models().get(modelAlias);
            if (config != null) {
                String apiKey = config.apiKey();
                if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("$")) {
                    String provider = config.provider() == null ? "openai" : config.provider().toLowerCase();
                    boolean logReq = config.logRequests() != null && config.logRequests();
                    boolean logResp = config.logResponses() != null && config.logResponses();
                    int retries = config.maxRetries() != null ? config.maxRetries() : 2;

                    if ("anthropic".equals(provider)) {
                        log.info("创建流式 Anthropic 模型 [{}]: model={}", modelAlias, config.modelName());
                        return AnthropicStreamingChatModel.builder()
                                .apiKey(apiKey)
                                .modelName(config.modelName())
                                .logRequests(logReq)
                                .logResponses(logResp)
                                .build();
                    } else if ("openai".equals(provider)) {
                        log.info("创建流式 OpenAI 兼容模型 [{}]: baseUrl={}, model={}", modelAlias, config.baseUrl(), config.modelName());
                        return OpenAiStreamingChatModel.builder()
                                .baseUrl(config.baseUrl())
                                .apiKey(apiKey)
                                .modelName(config.modelName())
                                .logRequests(logReq)
                                .logResponses(logResp)
                                .build();
                    }
                }
            }
        }

        // 回退逻辑
        if (properties.models() != null) {
            for (Map.Entry<String, ModelConfig> entry : properties.models().entrySet()) {
                ModelConfig config = entry.getValue();
                String apiKey = config.apiKey();
                if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("$")) {
                    String provider = config.provider() == null ? "openai" : config.provider().toLowerCase();
                    boolean logReq = config.logRequests() != null && config.logRequests();
                    boolean logResp = config.logResponses() != null && config.logResponses();
                    int retries = config.maxRetries() != null ? config.maxRetries() : 2;

                    if ("anthropic".equals(provider)) {
                        log.warn("Agent [{}] 未配置或配置的流式模型不可用，回退至第一个可用流式模型 [{}]", agentName, entry.getKey());
                        return AnthropicStreamingChatModel.builder()
                                .apiKey(apiKey)
                                .modelName(config.modelName())
                                .logRequests(logReq)
                                .logResponses(logResp)
                                .build();
                    } else if ("openai".equals(provider)) {
                        log.warn("Agent [{}] 未配置或配置的流式模型不可用，回退至第一个可用流式模型 [{}]", agentName, entry.getKey());
                        return OpenAiStreamingChatModel.builder()
                                .baseUrl(config.baseUrl())
                                .apiKey(apiKey)
                                .modelName(config.modelName())
                                .logRequests(logReq)
                                .logResponses(logResp)
                                .build();
                    }
                }
            }
        }

        log.error("❌ Agent [{}] 没有任何可用的流式 AI 模型！", agentName);
        return new NoOpStreamingModel();
    }

    // ======================== Agent 创建 ========================

    /**
     * 创建需求分析 Agent（结构化输出，无工具）。
     *
     * @return 需求分析 Agent 实例
     */
    public RequirementAnalyzer createRequirementAnalyzer() {
        return AiServices.builder(RequirementAnalyzer.class)
                .chatModel(resolveModel(AgentType.REQUIREMENT_ANALYSIS))
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt(AgentType.REQUIREMENT_ANALYSIS.getType()))
                .build();
    }

    /**
     * 创建执行规划 Agent（结构化输出，无工具）。
     *
     * @return 执行规划 Agent 实例
     */
    public ExecutionPlanner createExecutionPlanner() {
        return AiServices.builder(ExecutionPlanner.class)
                .chatModel(resolveModel(AgentType.EXECUTION_PLANNING))
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt(AgentType.EXECUTION_PLANNING.getType()))
                .build();
    }

    /**
     * 创建代码生成 Agent，注册 writeFile / readFileContext / validateCode 工具。
     *
     * <p>这是唯一应该使用贵模型的 Agent——代码质量直接决定构建成功率。</p>
     *
     * @return 代码生成 Agent 实例
     */
    public CodeGenAgent createCodeGenAgent() {
        return AiServices.builder(CodeGenAgent.class)
                .streamingChatModel(resolveStreamingModel(AgentType.CODE_GENERATION))
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt(AgentType.CODE_GENERATION.getType()))
                .build();
    }

    /**
     * 创建样式优化 Agent，注册 readFileContext / patchFile 工具。
     *
     * @return 样式优化 Agent 实例
     */
    public StyleOptimizationAgent createStyleOptimizationAgent() {
        return AiServices.builder(StyleOptimizationAgent.class)
                .chatModel(resolveModel(AgentType.STYLE_OPTIMIZATION))
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt(AgentType.STYLE_OPTIMIZATION.getType()))
                .tools(fileTools, projectContextTools)
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();
    }

    /**
     * 创建迭代修改 Agent，注册 readFileContext / searchCode / patchFile / writeFile 工具。
     *
     * @return 迭代修改 Agent 实例
     */
    public IterationAgent createIterationAgent() {
        return AiServices.builder(IterationAgent.class)
                .chatModel(resolveModel(AgentType.ITERATION_MODIFICATION))
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt(AgentType.ITERATION_MODIFICATION.getType()))
                .tools(fileTools, projectContextTools, iterationTools)
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();
    }

    // ======================== 占位模型 ========================

    /**
     * 无 API Key 时的占位模型，让 Spring 上下文能正常加载。
     * 实际调用任何 chat 方法都会抛出明确错误，提示用户配置环境变量。
     */
    private static class NoOpModel implements ChatModel {

        private static final String ERROR_MSG =
                "AI 模型未配置！请设置环境变量（如 DEEPSEEK_API_KEY、ANTHROPIC_API_KEY）后重启服务。";

        @Override
        public String chat(String message) {
            throw new IllegalStateException(ERROR_MSG);
        }

        @Override
        public ChatResponse chat(
                ChatRequest request) {
            throw new IllegalStateException(ERROR_MSG);
        }

        @Override
        public ChatResponse chat(
                ChatRequest request,
                ChatRequestOptions options) {
            throw new IllegalStateException(ERROR_MSG);
        }

        @Override
        public ChatResponse doChat(
                ChatRequest request) {
            throw new IllegalStateException(ERROR_MSG);
        }

        @Override
        public ChatResponse chat(
                ChatMessage... messages) {
            throw new IllegalStateException(ERROR_MSG);
        }

        @Override
        public ChatResponse chat(
                java.util.List<ChatMessage> messages) {
            throw new IllegalStateException(ERROR_MSG);
        }

        @Override
        public dev.langchain4j.model.chat.request.ChatRequestParameters defaultRequestParameters() {
            return null;
        }

        @Override
        public java.util.List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners() {
            return java.util.List.of();
        }

        @Override
        public dev.langchain4j.model.ModelProvider provider() {
            return dev.langchain4j.model.ModelProvider.OTHER;
        }

        @Override
        public java.util.Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
            return java.util.Set.of();
        }
    }

    /**
     * 无 API Key 时的占位流式模型，让 Spring 上下文能正常加载。
     */
    private static class NoOpStreamingModel implements StreamingChatModel {
        private static final String ERROR_MSG =
                "AI 模型未配置！请设置环境变量（如 DEEPSEEK_API_KEY、ANTHROPIC_API_KEY）后重启服务。";

        @Override
        public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
            throw new IllegalStateException(ERROR_MSG);
        }

        @Override
        public void chat(ChatRequest request, ChatRequestOptions options, StreamingChatResponseHandler handler) {
            throw new IllegalStateException(ERROR_MSG);
        }
    }
}
