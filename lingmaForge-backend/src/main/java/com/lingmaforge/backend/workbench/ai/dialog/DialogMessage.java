package com.lingmaforge.backend.workbench.ai.dialog;

import java.io.Serializable;

/**
 * 对话消息记录（role + content）。
 *
 * <p>用于 {@link DialogState#MESSAGES} 历史窗口。Phase 1 预留，Phase 2 多轮上下文填充。</p>
 *
 * @param role    角色：user / assistant
 * @param content 消息内容
 */
public record DialogMessage(String role, String content) implements Serializable {
    private static final long serialVersionUID = 1L;
}
