package com.lingmaforge.backend.workbench.ai.factory;

import lombok.Getter;

/**
 * 智能体（Agent）类型枚举。
 * 包含顺序、描述、类型/标识符三个字段。
 */
@Getter
public enum AgentType {

    REQUIREMENT_ANALYSIS(1, "需求分析", "requirement-analysis"),
    EXECUTION_PLANNING(2, "执行规划", "execution-planning"),
    CODE_GENERATION(3, "代码生成", "code-generation"),
    STYLE_OPTIMIZATION(4, "样式优化", "style-optimization"),
    ITERATION_MODIFICATION(5, "迭代修改", "iteration-modification");

    /** 顺序 */
    private final int order;
    /** 描述 */
    private final String description;
    /** 类型/标识符 */
    private final String type;

    AgentType(int order, String description, String type) {
        this.order = order;
        this.description = description;
        this.type = type;
    }

    /**
     * 根据类型标识获取枚举。
     *
     * @param type 类型标识
     * @return AgentType，若未匹配则返回 null
     */
    public static AgentType fromType(String type) {
        for (AgentType agentType : values()) {
            if (agentType.getType().equalsIgnoreCase(type)) {
                return agentType;
            }
        }
        return null;
    }
}
