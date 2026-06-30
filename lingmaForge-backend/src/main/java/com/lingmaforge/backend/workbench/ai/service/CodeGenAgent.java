package com.lingmaforge.backend.workbench.ai.service;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码生成 Agent 的 AiServices 接口契约。
 *
 * <p>不再携带工具，改为流式大语言模型调用。由 AiServices 驱动流式生成，并返回 TokenStream 进行细粒度推送。</p>
 */
public interface CodeGenAgent {

    /**
     * 生成单个文件的代码内容并通过 TokenStream 流式推送。
     *
     * @param prompt 当前文件的生成指令（含上下文、需求规格、可用文件）
     * @return 用于订阅 Token 流的 TokenStream
     */
    @UserMessage("{{prompt}}")
    TokenStream generate(@V("prompt") String prompt);
}
