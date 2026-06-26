package com.lingmaforge.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lingmaforge.backend.ai.pipeline.CodeGenPipeline;

import dev.langchain4j.model.chat.ChatModel;

/**
 * 验证后端已接入 LangChain4j 与 LangGraph4j，且流水线已编译。
 */
@SpringBootTest
class AiFrameworkConfigurationTests {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<CodeGenPipeline> pipelineProvider;

    @Autowired
    AiFrameworkConfigurationTests(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<CodeGenPipeline> pipelineProvider) {
        this.chatModelProvider = chatModelProvider;
        this.pipelineProvider = pipelineProvider;
    }

    /**
     * 确认 ChatModel 与编译后的 CodeGenPipeline Bean 已在 Spring 中可用。
     */
    @Test
    void exposesLangChain4jAndLangGraph4jBeans() {
        assertThat(chatModelProvider.getIfAvailable()).isNotNull();
        assertThat(pipelineProvider.getIfAvailable()).isNotNull();
        assertThat(pipelineProvider.getObject().getCompiledGraph()).isNotNull();
    }
}
