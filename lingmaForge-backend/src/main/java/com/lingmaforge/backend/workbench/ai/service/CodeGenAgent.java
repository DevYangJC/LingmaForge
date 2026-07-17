package com.lingmaforge.backend.workbench.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码生成 Agent——已从纯流式改为 agentic 工具循环。
 *
 * <p>模型通过 {@code writeFile/readFileContext/readProjectContext/updatePlan/exit} 工具
 * 自主"计划→读→写→更新进度→退出"，像 zero-code 那样顺序构建项目而非并行盲写。</p>
 */
public interface CodeGenAgent {

    @UserMessage("{{prompt}}")
    TokenStream generate(@MemoryId long memoryId, @V("prompt") String prompt);
}