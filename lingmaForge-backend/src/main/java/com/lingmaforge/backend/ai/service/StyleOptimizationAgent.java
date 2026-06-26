package com.lingmaforge.backend.ai.service;

import dev.langchain4j.service.UserMessage;

/**
 * 样式优化 Agent 的 AiServices 接口契约。
 *
 * <p>携带 readFileContext / patchFile 工具，对已生成样式做增量微调，
 * 保留用户手动修改。</p>
 */
public interface StyleOptimizationAgent {

    /**
     * 优化已生成文件的样式，通过 patchFile 工具应用增量补丁。
     *
     * @param prompt 样式优化指令（含已生成文件列表）
     * @return Agent 完成后的文本回复
     */
    String optimize(@UserMessage String prompt);
}
