package com.lingmaforge.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenPipeline;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.langchain4j.model.chat.ChatModel;

/**
 * 验证后端已接入 LangChain4j（多模型 Map）与 LangGraph4j（编译后的流水线）。
 */
@SpringBootTest
class AiFrameworkConfigurationTests {

    private static final Logger log = LoggerFactory.getLogger(AiFrameworkConfigurationTests.class);

    private final ObjectProvider<Map<String, ChatModel>> modelsProvider;
    private final ObjectProvider<CodeGenPipeline> pipelineProvider;

    @Autowired
    AiFrameworkConfigurationTests(
            ObjectProvider<Map<String, ChatModel>> modelsProvider,
            ObjectProvider<CodeGenPipeline> pipelineProvider) {
        this.modelsProvider = modelsProvider;
        this.pipelineProvider = pipelineProvider;
    }

    @Test
    void exposesLangChain4jAndLangGraph4jBeans() {
        log.info("========== AI 框架配置测试 ==========");

        Map<String, ChatModel> models = modelsProvider.getIfAvailable();
        assertThat(models).isNotNull();

        log.info("[OK] ChatModel Map Bean 存在");
        log.info("  已配置 {} 个模型: {}", models.size(),
                models.keySet().isEmpty() ? "(空 — 未设置 API Key，将使用 NoOpModel 降级)" : models.keySet());
        models.forEach((name, model) -> log.info("    - {} -> {} (provider: {})",
                name, model.getClass().getSimpleName(), model.provider()));

        CodeGenPipeline pipeline = pipelineProvider.getIfAvailable();
        assertThat(pipeline).isNotNull();
        assertThat(pipeline.getCompiledGraph()).isNotNull();

        log.info("[OK] CodeGenPipeline Bean 存在");
        log.info("  Graph: {}", pipeline.getCompiledGraph().getGraph(
                org.bsc.langgraph4j.GraphRepresentation.Type.MERMAID));
        log.info("========================================");
    }
}
