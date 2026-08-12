package com.lingmaforge.backend.common.model;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送对话消息请求体。
 *
 * @param message 用户消息内容，不能为空，最长 2000 字符
 */
public record SendMessageRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容不能超过 2000 个字符")
        String message) implements Serializable {

    private static final long serialVersionUID = 1L;
}
