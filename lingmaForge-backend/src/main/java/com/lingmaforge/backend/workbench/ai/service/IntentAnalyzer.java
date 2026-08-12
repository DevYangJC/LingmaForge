package com.lingmaforge.backend.workbench.ai.service;

import com.lingmaforge.backend.workbench.ai.dialog.IntentResult;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 意图识别 Agent 的 AiServices 接口契约。
 *
 * <p>利用 LangChain4j 的 {@code AiServices} 接口模式做结构化输出：
 * 返回类型即结构化类型 {@link IntentResult}，框架自动生成 JSON Schema、
 * 约束模型输出并反序列化，无需手动解析 JSON。</p>
 *
 * <p>system prompt 由 {@code AgentFactory} 通过 {@code systemMessageProvider} 注入，
 * 加载 {@code resources/prompts/intent-detection-system.txt}。</p>
 */
public interface IntentAnalyzer {

    /**
     * 识别用户消息的意图（generate_project / modify_code / chat）。
     *
     * @param userMessage 用户原始消息
     * @return 意图识别结果（含意图类型与置信度）
     */
    @UserMessage("{{userMessage}}")
    IntentResult analyze(@V("userMessage") String userMessage);
}
