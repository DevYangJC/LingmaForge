package com.lingmaforge.backend.workbench.ai.service;

import dev.langchain4j.service.UserMessage;

/**
 * 迭代修改 Agent 的 AiServices 接口契约。
 *
 * <p>携带 readFileContext / searchCode / patchFile / writeFile 工具，
 * 在已有项目基础上做增量修改：定位相关代码 → 生成 diff → 应用补丁。</p>
 */
public interface IterationAgent {

    /**
     * 根据用户的迭代修改指令，定位并修改相关代码。
     *
     * @param prompt 迭代修改指令（含用户指令、项目上下文、相关文件内容）
     * @return Agent 完成后的文本回复
     */
    String modify(@UserMessage String prompt);
}
