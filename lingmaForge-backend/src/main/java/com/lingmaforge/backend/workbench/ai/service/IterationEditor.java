package com.lingmaforge.backend.workbench.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.stereotype.Component;

/**
 * 对话式迭代编辑 Agent——独立于 {@link IterationAgent}（只做结构化规划），
 * 拥有完整的 {@code @Tool} 工具集（{@code writeFile/patchFile/readFileContext/readProjectContext/
 * updatePlan/exit}），以流式 {@link TokenStream} 驱动自主"边看边改"循环。
 *
 * <p>{@code @MemoryId} 用于多轮对话记忆，使连续多轮编辑保持上下文连续。</p>
 */
@Component
public interface IterationEditor {

    /**
     * 执行一次自主迭代修改，沿 agent 工具循环通过 {@link TokenStream} 推送
     * token / thinking / tool_call 事件。
     *
     * @param memoryId 记忆 ID（projectId + "_vue-project"），用于多轮对话记忆
     * @param prompt   完整的编辑指令（含项目上下文、修改计划、回退时的构建错误分析等）
     * @return 用于订阅 Token 流的 TokenStream
     */
    @UserMessage("{{prompt}}")
    TokenStream edit(@MemoryId long memoryId, @V("prompt") String prompt);
}