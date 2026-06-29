package com.lingmaforge.backend.workbench.ai.service;

import dev.langchain4j.service.UserMessage;

/**
 * 代码生成 Agent 的 AiServices 接口契约。
 *
 * <p>携带 {@code @Tool} 工具（writeFile / readFileContext / validateCode），
 * 由 {@code AiServices} 内部驱动 Reason→Act→Observe 循环，逐文件生成代码并写入磁盘。</p>
 *
 * <p>每次调用生成一个文件，外部节点通过遍历执行规划中的文件清单循环调用本接口（方案 B：
 * 外部循环 + 单次调用内 Agent 循环），可控且上下文窗口友好。</p>
 */
public interface CodeGenAgent {

    /**
     * 生成单个文件的代码内容并通过 writeFile 工具写入。
     *
     * @param prompt 当前文件的生成指令（含上下文、需求规格、可用文件）
     * @return Agent 完成后的文本回复
     */
    String generate(@UserMessage String prompt);
}
