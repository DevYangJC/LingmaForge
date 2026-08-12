package com.lingmaforge.backend.workbench.ai.service;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 闲聊回复 Agent 的 AiServices 接口契约。
 *
 * <p>由 {@code AgentFactory.createChatReplyAgent()} 创建，注入 StreamingChatModel
 * 与 chat-reply system prompt。调用方通过 {@link #reply(String)} 获取 {@link TokenStream}，
 * 订阅 {@code onPartialResponse} 逐 token 推送 SSE。</p>
 *
 * <p>镜像 {@link CodeGenAgent} 的流式模式，但无工具、无文件生成——纯对话回复。</p>
 */
public interface ChatReplyAgent {

    /**
     * 流式回复用户消息。
     *
     * @param userMessage 用户原始消息
     * @return TokenStream，订阅后逐 token 推送
     */
    @UserMessage("{{userMessage}}")
    TokenStream reply(@V("userMessage") String userMessage);
}
