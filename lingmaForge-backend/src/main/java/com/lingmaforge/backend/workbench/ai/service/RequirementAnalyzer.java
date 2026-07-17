package com.lingmaforge.backend.workbench.ai.service;

import com.lingmaforge.backend.common.model.RequirementSpec;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 需求分析 Agent 的 AiServices 接口契约。
 */
public interface RequirementAnalyzer {

    /** 结构化同步调用（保留用于测试和向后兼容）。 */
    @UserMessage("{{userPrompt}}")
    RequirementSpec analyze(@V("userPrompt") String userPrompt);

    /** 流式调用——通过 TokenStream 推送 thinking token，需手动解析 JSON 结果。 */
    @UserMessage("{{userPrompt}}")
    TokenStream analyzeStream(@MemoryId long memoryId, @V("userPrompt") String userPrompt);
}