package com.lingmaforge.backend.workbench.ai.node;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.ExecutionPlanner;
import com.lingmaforge.backend.common.model.PlanResult;
import com.lingmaforge.backend.common.model.RequirementSpec;

/**
 * 节点二：执行规划。
 *
 * <p>把需求规格拆解为文件清单、生成顺序与每个文件的职责，输出 {@link PlanResult}。
 * 不需要 Agent 循环（无工具调用），使用 AiServices 单次调用。</p>
 */
@Component
public class ExecutionPlanningNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPlanningNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "execution_planning";

    private final ExecutionPlanner planner;

    public ExecutionPlanningNode(AgentFactory agentFactory, GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.planner = agentFactory.createExecutionPlanner();
    }

    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在规划文件清单与构建计划...");
        try {
            RequirementSpec analysisResult = state.analysisResult().orElseThrow();
            String userPrompt = "请根据以下需求规范规划文件清单：\n" + describeRequirement(analysisResult);
            log.info("[{}] 执行规划开始", state.taskId().orElse(""));
            PlanResult plan = planner.plan(userPrompt);
            emitter.emitNode(NODE_NAME,
                    "执行规划完成：" + plan.files().size() + " 个文件", "TEXT");
            return Map.of(CodeGenState.PLAN_RESULT, plan);
        } catch (Exception e) {
            log.error("[{}] 执行规划失败", state.taskId().orElse(""), e);
            emitter.emitNode(NODE_NAME, "执行规划失败: " + e.getMessage(), "TEXT");
            throw e;
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }

    private String describeRequirement(RequirementSpec spec) {
        return "应用名称: %s\n描述: %s\n页面数: %d\nAPI 数: %d\n风格: %s".formatted(
                spec.appName(),
                spec.description(),
                spec.pages() == null ? 0 : spec.pages().size(),
                spec.apis() == null ? 0 : spec.apis().size(),
                spec.style() == null ? "默认" : spec.style().themeName());
    }
}
