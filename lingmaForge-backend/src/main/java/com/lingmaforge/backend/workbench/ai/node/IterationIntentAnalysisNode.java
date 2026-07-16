package com.lingmaforge.backend.workbench.ai.node;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.common.model.IterationIntent;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;

/**
 * 对话式迭代的意图分析节点。
 *
 * <p>负责把用户在 IDE 对话窗口输入的自然语言修改请求，转换成结构化的
 * {@link IterationIntent}。该节点只识别本轮修改目标，不直接读写项目文件。</p>
 */
@Component
public class IterationIntentAnalysisNode extends AbstractCodeGenNode {

    /** 节点名称，对应 LangGraph4j 状态图和 SSE 事件。 */
    public static final String NODE_NAME = "iteration_intent_analysis";

    private final IterationAgent iterationAgent;

    @Autowired
    public IterationIntentAnalysisNode(AgentFactory agentFactory, GenerationStreamRegistry streamRegistry) {
        this(agentFactory.createIterationAgent(), streamRegistry);
    }

    public IterationIntentAnalysisNode(IterationAgent iterationAgent, GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.iterationAgent = iterationAgent;
    }

    /**
     * 执行本轮对话意图识别。
     *
     * @param state 当前代码生成状态
     * @return 状态更新，写入 iterationIntent
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在理解本轮修改意图...");
        try {
            String prompt = state.iterationPrompt().or(() -> state.prompt()).orElse("");
            String projectContext = state.iterationContext().orElse("");
            IterationIntent intent = iterationAgent.analyzeIntent(prompt, projectContext);
            if (emitter != null) {
                emitter.emitNode(NODE_NAME, "已识别修改意图：" + intent.summary(), "TEXT");
            }
            return Map.of(CodeGenState.ITERATION_INTENT, intent);
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }
}
