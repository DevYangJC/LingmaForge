package com.lingmaforge.backend.workbench.ai.service;

import com.lingmaforge.backend.common.model.RequirementSpec;

import dev.langchain4j.service.UserMessage;

/**
 * 需求分析 Agent 的 AiServices 接口契约。
 *
 * <p>利用 LangChain4j 的 {@code AiServices} 接口模式做结构化输出：
 * 返回类型即结构化类型，框架自动生成 JSON Schema、约束模型输出并反序列化为 {@link RequirementSpec}，
 * 无需手动解析 JSON。</p>
 *
 * <p>system prompt 由 {@code AgentFactory} 通过 {@code systemMessageProvider} 注入。</p>
 */
public interface RequirementAnalyzer {

    /**
     * 将用户自然语言需求解析为结构化需求规格。
     *
     * @param userPrompt 用户原始需求
     * @return 结构化需求规格
     */
    RequirementSpec analyze(@UserMessage String userPrompt);
}
