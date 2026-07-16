package com.lingmaforge.backend.workbench.ai.node;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.common.model.BuildErrorAnalysis;
import com.lingmaforge.backend.common.model.ModificationPlan;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;

/**
 * 对话式迭代的构建错误分析节点。
 *
 * <p>当修改后构建失败时，本节点把构建日志和本轮修改计划交给迭代 Agent，输出结构化
 * {@link BuildErrorAnalysis}，供下一轮修复规划或前端错误解释使用。</p>
 */
@Component
public class BuildErrorAnalysisNode extends AbstractCodeGenNode {

    /** 节点名称，对应 LangGraph4j 状态图和 SSE 事件。 */
    public static final String NODE_NAME = "build_error_analysis";

    private final IterationAgent iterationAgent;

    @Autowired
    public BuildErrorAnalysisNode(AgentFactory agentFactory, GenerationStreamRegistry streamRegistry) {
        this(agentFactory.createIterationAgent(), streamRegistry);
    }

    public BuildErrorAnalysisNode(IterationAgent iterationAgent, GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.iterationAgent = iterationAgent;
    }

    /**
     * 分析构建失败原因。
     *
     * @param state 当前代码生成状态
     * @return 状态更新，写入 buildErrorAnalysis
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在分析构建错误...");
        try {
            String buildLog = state.buildError().orElse("");
            ModificationPlan plan = state.modificationPlan()
                    .orElseGet(() -> new ModificationPlan("", List.of(), List.of()));
            BuildErrorAnalysis analysis = iterationAgent.analyzeBuildError(buildLog, plan);
            if (emitter != null) {
                emitter.emitNode(NODE_NAME, "已完成构建错误分析：" + analysis.summary(), "TEXT");
            }
            return Map.of(CodeGenState.BUILD_ERROR_ANALYSIS, analysis);
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }
}
