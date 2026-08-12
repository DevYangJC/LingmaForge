package com.lingmaforge.backend.common.model;

import java.io.Serializable;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求体。
 *
 * @param projectId 关联项目 ID，可空（闲聊无项目）
 * @param title     会话标题，可空（默认 "新对话"），最长 80 字符
 */
public record CreateDialogRequest(
        Long projectId,

        @Size(max = 80, message = "会话标题不能超过 80 个字符")
        String title) implements Serializable {

    private static final long serialVersionUID = 1L;
}
