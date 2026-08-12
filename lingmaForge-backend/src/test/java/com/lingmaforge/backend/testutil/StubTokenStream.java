package com.lingmaforge.backend.testutil;

import java.util.List;
import java.util.function.Consumer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;

/**
 * 测试用 {@link TokenStream} 桩实现。
 *
 * <p>持有一段预设内容，在 {@link #start()} 时依次触发 {@code onPartialThinking}
 * → {@code onPartialResponse}（整段内容作为单个 token）→ {@code onCompleteResponse} 回调，
 * 模拟大模型流式输出完成的过程。未订阅的回调被安全跳过。</p>
 *
 * <p>原为 {@code PipelineNodesTest} 的 private static 内部类，Phase 2 提取为顶层类，
 * 供 {@code PipelineNodesTest} 与 {@code DialogRouterTest}（以及未来的闲聊相关测试）共用。</p>
 */
public class StubTokenStream implements TokenStream {

    private Consumer<String> partialResponseConsumer;
    private Consumer<ChatResponse> completeResponseConsumer;
    private Consumer<PartialThinking> partialThinkingConsumer;
    private final String content;

    /**
     * @param content 流式输出的完整文本（作为一个 token 推送）
     */
    public StubTokenStream(String content) {
        this.content = content;
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> consumer) {
        this.partialResponseConsumer = consumer;
        return this;
    }

    @Override
    public TokenStream onPartialThinking(Consumer<PartialThinking> consumer) {
        this.partialThinkingConsumer = consumer;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> consumer) {
        return this;
    }

    @Override
    public TokenStream onToolExecuted(Consumer<ToolExecution> consumer) {
        return this;
    }

    @Override
    public TokenStream onCompleteResponse(Consumer<ChatResponse> consumer) {
        this.completeResponseConsumer = consumer;
        return this;
    }

    @Override
    public TokenStream onError(Consumer<Throwable> consumer) {
        return this;
    }

    @Override
    public TokenStream ignoreErrors() {
        return this;
    }

    @Override
    public void start() {
        if (partialThinkingConsumer != null) {
            partialThinkingConsumer.accept(new PartialThinking(
                    "Thinking: Planning layout for "
                            + content.substring(0, Math.min(15, content.length())) + "..."));
        }
        if (partialResponseConsumer != null) {
            partialResponseConsumer.accept(content);
        }
        if (completeResponseConsumer != null) {
            completeResponseConsumer.accept(ChatResponse.builder()
                    .aiMessage(AiMessage.from(content))
                    .build());
        }
    }
}
