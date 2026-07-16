package com.lingmaforge.backend.workbench.ai.tool;

import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.Tool;

/**
 * 退出工具——移植自 zero-code {@code ExitTool}，让模型在完成所有修改后明确终止 agent 循环。
 *
 * <p>LangChain4j AiServices 的 agent 循环在模型回复不含工具调用时自动终止，
 * 但某些模型会反复追加空文本/同义反复导致超长循环。显式 {@code exit()} 工具提供
 * 一个确定性终止信号，配合 PlanTracker Nag 共同构成防跑偏机制。</p>
 */
@Component
public class ExitTool {

    /**
     * 模型主动标记修改完成，终止 agent 循环。
     *
     * @return 完成确认文本
     */
    @Tool("标记所有修改已完成并退出，调用此工具后不要再做其它操作")
    public String exit() {
        return "所有修改已完成。exit 已确认，agent 循环终止。";
    }
}