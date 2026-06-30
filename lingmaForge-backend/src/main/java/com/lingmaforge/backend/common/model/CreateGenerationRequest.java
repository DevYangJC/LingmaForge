package com.lingmaforge.backend.common.model;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建生成任务的请求体。
 *
 * @param projectId 项目 ID，不能为空
 * @param prompt    需求描述，最长 2000 个字符
 */
public record CreateGenerationRequest(
        @NotNull(message = "项目 ID 不能为空")
        Long projectId,

        @NotBlank(message = "需求不能为空")
        @Size(max = 2000, message = "需求不能超过 2000 个字符")
        String prompt) implements Serializable {

    private static final long serialVersionUID = 1L;
}
