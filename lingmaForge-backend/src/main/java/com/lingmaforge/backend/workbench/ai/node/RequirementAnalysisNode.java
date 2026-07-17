package com.lingmaforge.backend.workbench.ai.node;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.RequirementAnalyzer;
import com.lingmaforge.backend.workbench.ai.stream.StreamingBridge;
import com.lingmaforge.backend.workbench.ai.stream.StreamingContext;
import com.lingmaforge.backend.common.model.RequirementSpec;

/**
 * 需求分析节点——已切到流式 Thinking 推送。
 *
 * <p>旧版用同步 ChatModel 等 5-15s 无任何输出。
 * 新版通过 StreamingBridge 驱动 analyzeStream TokenStream，thinking token 实时推前端。</p>
 */
@Component
public class RequirementAnalysisNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(RequirementAnalysisNode.class);

    public static final String NODE_NAME = "requirement_analysis";

    private final RequirementAnalyzer analyzer;
    private final ObjectMapper objectMapper;
    private final StreamingBridge streamingBridge;

    public RequirementAnalysisNode(AgentFactory agentFactory,
            GenerationStreamRegistry streamRegistry,
            ObjectMapper objectMapper,
            StreamingBridge streamingBridge) {
        super(streamRegistry);
        this.analyzer = agentFactory.createRequirementAnalyzer();
        this.objectMapper = objectMapper;
        this.streamingBridge = streamingBridge;
    }

    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在分析需求...");
        Long projectId = projectId(state);
        String taskId = state.taskId().orElse("");
        String prompt = state.prompt().orElseThrow();

        try {
            StringBuilder acc = new StringBuilder();
            CompletableFuture<RequirementSpec> future = new CompletableFuture<>();

            StreamingContext ctx = StreamingContext.builder()
                    .emitter(emitter)
                    .nodeName(NODE_NAME)
                    .taskId(taskId)
                    .stopRegistry(getStreamRegistry())
                    .onToken(acc::append)
                    .onComplete(() -> {
                        try {
                            String sanitizedJson = cleanJsonString(acc.toString());
                            future.complete(objectMapper.readValue(sanitizedJson, RequirementSpec.class));
                        } catch (Exception e) {
                            future.completeExceptionally(
                                    new RuntimeException("需求分析结果解析失败: " + e.getMessage(), e));
                        }
                    })
                    .onStop(text -> future.completeExceptionally(new RuntimeException("任务已停止")))
                    .build();

            streamingBridge.bridge(analyzer.analyzeStream(projectId, prompt), ctx);

            RequirementSpec spec = future.get(120, TimeUnit.SECONDS);
            return Map.of(CodeGenState.ANALYSIS_RESULT, spec);
        } catch (Exception e) {
            log.error("[{}] 需求分析失败", taskId, e);
            emitter.emitNode(NODE_NAME, "需求分析失败: " + e.getMessage(), "TEXT");
            throw new RuntimeException("需求分析失败", e);
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }
}