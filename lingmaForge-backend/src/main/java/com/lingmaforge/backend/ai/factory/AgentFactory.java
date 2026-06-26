package com.lingmaforge.backend.ai.factory;

import org.springframework.stereotype.Component;

import com.lingmaforge.backend.ai.service.CodeGenAgent;
import com.lingmaforge.backend.ai.service.ExecutionPlanner;
import com.lingmaforge.backend.ai.service.IterationAgent;
import com.lingmaforge.backend.ai.service.RequirementAnalyzer;
import com.lingmaforge.backend.ai.service.StyleOptimizationAgent;
import com.lingmaforge.backend.ai.tool.FileTools;
import com.lingmaforge.backend.ai.tool.IterationTools;
import com.lingmaforge.backend.ai.tool.ProjectContextTools;
import com.lingmaforge.backend.service.PromptTemplateLoader;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Agent 创建工厂。
 *
 * <p>集中所有 AiServices 实例的创建逻辑，统一注入模型、system prompt 与工具。
 * 换模型、加工具、调参数只需修改本工厂，业务节点无需改动。</p>
 *
 * <p>两类创建模式：
 * <ul>
 *   <li><b>ai-service（无工具）</b>：需求分析、执行规划，单次调用返回结构化 Java 对象</li>
 *   <li><b>ai-service（带工具）</b>：代码生成、样式优化、迭代修改，内部驱动 Agent 循环</li>
 * </ul>
 * system prompt 由 {@link PromptTemplateLoader} 从 {@code resources/prompts} 加载。</p>
 */
@Component
public class AgentFactory {

    /** 单次文件生成内允许的最大工具调用往返数。 */
    private static final int MAX_TOOL_ROUND_TRIPS = 12;

    private final ChatModel chatModel;
    private final PromptTemplateLoader promptLoader;
    private final FileTools fileTools;
    private final ProjectContextTools projectContextTools;
    private final IterationTools iterationTools;

    public AgentFactory(ChatModel chatModel,
            PromptTemplateLoader promptLoader,
            FileTools fileTools,
            ProjectContextTools projectContextTools,
            IterationTools iterationTools) {
        this.chatModel = chatModel;
        this.promptLoader = promptLoader;
        this.fileTools = fileTools;
        this.projectContextTools = projectContextTools;
        this.iterationTools = iterationTools;
    }

    /**
     * 创建需求分析 Agent。
     *
     * @return 需求分析 Agent 实例
     */
    public RequirementAnalyzer createRequirementAnalyzer() {
        return AiServices.builder(RequirementAnalyzer.class)
                .chatModel(chatModel)
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt("requirement-analysis"))
                .build();
    }

    /**
     * 创建执行规划 Agent。
     *
     * @return 执行规划 Agent 实例
     */
    public ExecutionPlanner createExecutionPlanner() {
        return AiServices.builder(ExecutionPlanner.class)
                .chatModel(chatModel)
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt("execution-planning"))
                .build();
    }

    /**
     * 创建代码生成 Agent，注册 writeFile / readFileContext / validateCode 工具。
     *
     * @return 代码生成 Agent 实例
     */
    public CodeGenAgent createCodeGenAgent() {
        return AiServices.builder(CodeGenAgent.class)
                .chatModel(chatModel)
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt("code-generation"))
                .tools(fileTools, projectContextTools)
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();
    }

    /**
     * 创建样式优化 Agent，注册 readFileContext / patchFile 工具。
     *
     * @return 样式优化 Agent 实例
     */
    public StyleOptimizationAgent createStyleOptimizationAgent() {
        return AiServices.builder(StyleOptimizationAgent.class)
                .chatModel(chatModel)
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt("style-optimization"))
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
                .chatModel(chatModel)
                .systemMessageProvider(id -> promptLoader.loadSystemPrompt("iteration-modification"))
                .tools(fileTools, projectContextTools, iterationTools)
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();
    }
}
