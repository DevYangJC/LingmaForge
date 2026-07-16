package com.lingmaforge.backend.workbench.ai.factory;

import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import com.lingmaforge.backend.infra.config.LingmaModelsProperties.ModelConfig;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.service.CodeGenAgent;
import com.lingmaforge.backend.workbench.ai.service.ExecutionPlanner;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;
import com.lingmaforge.backend.workbench.ai.service.IterationEditor;
import com.lingmaforge.backend.workbench.ai.service.RequirementAnalyzer;
import com.lingmaforge.backend.workbench.ai.service.StyleOptimizationAgent;
import com.lingmaforge.backend.workbench.ai.tool.FileTools;
import com.lingmaforge.backend.workbench.ai.tool.ProjectContextTools;
import com.lingmaforge.backend.workbench.ai.tool.UpdatePlanTool;
import com.lingmaforge.backend.workbench.ai.tool.ExitTool;
import com.lingmaforge.backend.workbench.ai.plan.PlanTracker;
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
    private final UpdatePlanTool updatePlanTool;
    private final ExitTool exitTool;
    private final PlanTracker planTracker;
    private final Function<String, MessageWindowChatMemory> chatMemoryProvider;

    public AgentFactory(Map<String, ChatModel> chatModels,
            LingmaModelsProperties properties,
            PromptTemplateLoader promptLoader,
            FileTools fileTools,
            ProjectContextTools projectContextTools,
            UpdatePlanTool updatePlanTool,
            ExitTool exitTool,
            PlanTracker planTracker,
            Function<String, MessageWindowChatMemory> chatMemoryProvider) {
        this.chatModels = chatModels;
        this.properties = properties;
        this.agentConfigs = properties.agents() != null ? properties.agents() : Map.of();
        this.promptLoader = promptLoader;
        this.fileTools = fileTools;
        this.projectContextTools = projectContextTools;
        this.updatePlanTool = updatePlanTool;
        this.exitTool = exitTool;
        this.planTracker = planTracker;
        this.chatMemoryProvider = chatMemoryProvider;
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
     * <p>对 OpenAI 兼容协议（DeepSeek / 通义 / Moonshot 等）启用移植自 zero-code 的
     * <b>推理流式</b>能力：{@code returnThinking} 返回 reasoning_content 思考分片，
     * {@code sendThinking} 在后续工具调用请求中回放 reasoning_content —— 这是多轮工具调用
     * 下保持思维链连续、显著提升代码生成质量的<关键杠杆>。
     * 同时关闭 HTTP 压缩（{@code Accept-Encoding: identity}）规避代理截断，
     * 并按配置注入 {@code maxTokens}。</p>
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
                StreamingChatModel model = buildStreamingModel(modelAlias, config, false);
                if (model != null) {
                    return model;
                }
            }
        }

        // 回退逻辑：取第一个可用流式模型
        if (properties.models() != null) {
            for (Map.Entry<String, ModelConfig> entry : properties.models().entrySet()) {
                ModelConfig config = entry.getValue();
                StreamingChatModel model = buildStreamingModel(entry.getKey(), config, true);
                if (model != null) {
                    return model;
                }
            }
        }

        log.error("❌ Agent [{}] 没有任何可用的流式 AI 模型！", agentName);
        return new NoOpStreamingModel();
    }

    /**
     * 统一构建流式模型：按 provider 分发，对 OpenAI 兼容协议注入推理回放与抗代理截断配置。
     *
     * @param modelAlias 模型别名（仅用于日志）
     * @param config     模型连接配置
     * @param fallback   是否为回退链路（影响日志级别）
     * @return 构建成功的 StreamingChatModel；api-key 缺失或不支持的 provider 返回 null
     */
    private StreamingChatModel buildStreamingModel(String modelAlias, ModelConfig config, boolean fallback) {
        String apiKey = config.apiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("$")) {
            return null;
        }
        String provider = config.provider() == null ? "openai" : config.provider().toLowerCase();
        boolean logReq = config.logRequests() != null && config.logRequests();
        boolean logResp = config.logResponses() != null && config.logResponses();

        if ("anthropic".equals(provider)) {
            if (fallback) {
                log.warn("Agent 回退至流式 Anthropic 模型 [{}]: model={}", modelAlias, config.modelName());
            } else {
                log.info("创建流式 Anthropic 模型 [{}]: model={}", modelAlias, config.modelName());
            }
            return AnthropicStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(config.modelName())
                    .logRequests(logReq)
                    .logResponses(logResp)
                    .build();
        }
        if ("openai".equals(provider)) {
            if (fallback) {
                log.warn("Agent 回退至流式 OpenAI 兼容模型 [{}]: baseUrl={}, model={}", modelAlias, config.baseUrl(), config.modelName());
            } else {
                log.info("创建流式 OpenAI 兼容模型 [{}]: baseUrl={}, model={}", modelAlias, config.baseUrl(), config.modelName());
            }
            OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                    .baseUrl(config.baseUrl())
                    .apiKey(apiKey)
                    .modelName(config.modelName())
                    .logRequests(logReq)
                    .logResponses(logResp);
            if (config.maxTokens() != null) {
                builder.maxTokens(config.maxTokens());
            }
            // 推理回放（移植自 zero-code ReasoningStreamingChatModelConfig）：
            // returnThinking 让 onPartialThinking 回调能拿到 reasoning_content；
            // sendThinking 把上一轮思考回放进下一轮请求，保证多轮工具调用的思维链连续。
            boolean returnThinking = config.returnThinking() == null || config.returnThinking();
            boolean sendThinking = config.sendThinking() == null || config.sendThinking();
            String thinkingField = config.thinkingField() == null || config.thinkingField().isBlank()
                    ? "reasoning_content" : config.thinkingField();
            builder.returnThinking(returnThinking);
            builder.sendThinking(sendThinking, thinkingField);
            // 关闭 HTTP 压缩，降低代理/DNS 抖动导致的流式中断概率
            builder.customHeaders(Map.of("Accept-Encoding", "identity"));
            return builder.build();
        }
        log.warn("流式模型 [{}] 的 provider [{}] 不支持，跳过。支持: openai, anthropic", modelAlias, provider);
        return null;
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
                .chatMemoryProvider(id -> chatMemoryProvider.apply(id.toString() + "_vue-project"))
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
                .build();
    }

    /**
     * 创建对话式迭代编辑 Agent——带完整工具集的流式 agentic 循环。
     *
     * <p>工具集：FileTools(writeFile/patchFile/validateCode) + ProjectContextTools(readFileContext/readProjectContext)
     * + UpdatePlanTool(updatePlan) + ExitTool(exit)。其中 UpdatePlanTool 与 PlanTracker 联动提供
     * DAG 校验 + Nag 防跑偏，ExitTool 保证模型终止循环而非无限自说自话。
     * 使用 {@code iteration-modification} 模型别名并走 reasoning 流式通道，
     * 让迭代编辑也享受 reasoning_content 回放的思维链连续性。</p>
     *
     * @return 迭代编辑 Agent 实例
     */
    public IterationEditor createIterationEditor() {
        return AiServices.builder(IterationEditor.class)
                .streamingChatModel(resolveStreamingModel(AgentType.ITERATION_MODIFICATION))
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt(AgentType.ITERATION_MODIFICATION.getType()))
                .chatMemoryProvider(id -> chatMemoryProvider.apply(id.toString() + "_vue-project"))
                .tools(fileTools, projectContextTools, updatePlanTool, exitTool)
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
