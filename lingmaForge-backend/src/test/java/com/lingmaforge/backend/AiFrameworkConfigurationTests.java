package com.lingmaforge.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lingmaforge.backend.generation.pipeline.CodeGenPipeline;

import dev.langchain4j.model.chat.ChatModel;

/**
 * 验证后端已接入 LangChain4j（多模型 Map）与 LangGraph4j（编译后的流水线）。
 */
@SpringBootTest
class AiFrameworkConfigurationTests {

    private final ObjectProvider<Map<String, ChatModel>> modelsProvider;
    private final ObjectProvider<CodeGenPipeline> pipelineProvider;

    @Autowired
    AiFrameworkConfigurationTests(
            ObjectProvider<Map<String, ChatModel>> modelsProvider,
            ObjectProvider<CodeGenPipeline> pipelineProvider) {
        this.modelsProvider = modelsProvider;
        this.pipelineProvider = pipelineProvider;
    }

    /**
     * 确认 LangChain4j 模型 Map 与编译后的 CodeGenPipeline 在 Spring 中可用。
     * 即使没有任何 API Key 配置，Map 也应存在（可为空），流水线应成功编译。
     */
    @Test
    void exposesLangChain4jAndLangGraph4jBeans() {
        assertThat(modelsProvider.getIfAvailable()).isNotNull();
        assertThat(pipelineProvider.getIfAvailable()).isNotNull();
        assertThat(pipelineProvider.getObject().getCompiledGraph()).isNotNull();
    }
}
