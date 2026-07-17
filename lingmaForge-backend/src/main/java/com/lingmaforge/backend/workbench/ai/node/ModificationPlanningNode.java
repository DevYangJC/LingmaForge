package com.lingmaforge.backend.workbench.ai.node;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.common.model.IterationIntent;
import com.lingmaforge.backend.common.model.ModificationPlan;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;

/**
 * 对话式迭代的修改规划节点。
 *
 * <p>负责把用户意图和项目上下文转换为结构化 {@link ModificationPlan}。规划结果是
 * 文件补丁节点的唯一输入，从而让 LLM 生成与文件落库动作解耦。</p>
 */
@Component
public class ModificationPlanningNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(ModificationPlanningNode.class);

    /** 节点名称，对应 LangGraph4j 状态图和 SSE 事件。 */
    public static final String NODE_NAME = "modification_planning";

    private final IterationAgent iterationAgent;

    @Autowired
    public ModificationPlanningNode(AgentFactory agentFactory, GenerationStreamRegistry streamRegistry) {
        this(agentFactory.createIterationAgent(), streamRegistry);
    }

    public ModificationPlanningNode(IterationAgent iterationAgent, GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.iterationAgent = iterationAgent;
    }

    /**
     * 执行本轮文件修改规划。
     *
     * @param state 当前代码生成状态
     * @return 状态更新，写入 modificationPlan
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在规划本轮代码修改...");
        try {
            String prompt = state.iterationPrompt().or(() -> state.prompt()).orElse("");
            String projectContext = state.iterationContext().orElse("");
            IterationIntent intent = state.iterationIntent()
                    .orElseGet(() -> new IterationIntent("unknown", prompt, List.of(), true));
            var buildErrorAnalysis = state.buildErrorAnalysis().orElse(null);
            log.info("[{}] 开始生成迭代修改计划: intentType={}, hasBuildErrorAnalysis={}",
                    state.taskId().orElse(""), intent.type(), buildErrorAnalysis != null);
            ModificationPlan plan = iterationAgent.planModification(prompt, projectContext, intent, buildErrorAnalysis);
            return Map.of(CodeGenState.MODIFICATION_PLAN, plan);
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }
}
