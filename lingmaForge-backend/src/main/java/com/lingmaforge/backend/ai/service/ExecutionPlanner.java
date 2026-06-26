package com.lingmaforge.backend.ai.service;

import com.lingmaforge.backend.model.PlanResult;

import dev.langchain4j.service.UserMessage;

/**
 * 执行规划 Agent 的 AiServices 接口契约。
 *
 * <p>根据需求规格输出文件清单与生成顺序，返回类型即结构化类型 {@link PlanResult}。</p>
 */
public interface ExecutionPlanner {

    /**
     * 根据需求规格规划文件清单与生成顺序。
     *
     * @param requirementJson 需求规格的文本描述
     * @return 执行规划结果
     */
    PlanResult plan(@UserMessage String requirementJson);
}
