package com.lingmaforge.backend.workbench.ai.tool;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.plan.PlanItem;
import com.lingmaforge.backend.workbench.ai.plan.PlanTracker;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 计划管理工具——移植自 zero-code {@code UpdatePlanTool}，使模型能够结构化地列出现有任务并跟踪进度。
 *
 * <p>模型在 agent 循环中调用此工具来声明"我正在做什么、已完成什么、接下来做什么"。
 * PlanTracker 负责校验 DAG（依赖正确、in_progress 前置项必已完成），并返回格式化进度。</p>
 */
@Component
public class UpdatePlanTool {

    private static final Logger log = LoggerFactory.getLogger(UpdatePlanTool.class);

    private final PlanTracker planTracker;

    public UpdatePlanTool(PlanTracker planTracker) {
        this.planTracker = planTracker;
    }

    /**
     * 全量替换当前修改计划。
     *
     * @param items 计划项列表
     * @return 格式化进度文本
     */
    @Tool("更新当前修改计划。每次文件操作后调用此工具记录进度。参数 items 是计划项列表，每项含 id/name/status/dependency")
    public String updatePlan(@P("计划项列表，每项含 id(序号)/text(描述)/status: pending|in_progress|completed/deps(前置依赖id列表)") List<PlanItem> items) {
        try {
            GenerationContext ctx = GenerationContext.get();
            String key = PlanTracker.key(ctx.projectId(), ctx.taskId());
            String progress = planTracker.updatePlan(key, items);
            // 推送 tool_call 事件（完成阶段）
            ctx.emitter().emitToolCall("updatePlan", "updatePlan", items.size() + " items", progress);
            log.debug("updatePlan 工具调用: key={}, items={}", key, items.size());
            return progress;
        } catch (IllegalArgumentException e) {
            log.warn("updatePlan 校验失败: {}", e.getMessage());
            return "计划更新失败：" + e.getMessage() + "。请修正后重试。";
        }
    }
}