package com.lingmaforge.backend.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新项目文件内容的请求体。
 */
public record UpdateFileRequest(
        @NotBlank(message = "文件路径不能为空")
        String path,

        String content) {
}
