package com.lingmaforge.backend.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 迭代修改请求体，用于对已有生成结果发出修改指令。
 *
 * @param projectId 项目 ID，不能为空
 * @param prompt    修改指令描述，最长 2000 个字符
 */
public record IterateRequest(
        @NotNull(message = "项目 ID 不能为空")
        Long projectId,

        @NotBlank(message = "修改指令不能为空")
        @Size(max = 2000, message = "修改指令不能超过 2000 个字符")
        String prompt) {
}
