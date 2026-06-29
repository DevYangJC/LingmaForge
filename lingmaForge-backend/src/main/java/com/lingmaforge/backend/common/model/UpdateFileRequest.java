package com.lingmaforge.backend.common.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新项目文件内容的请求体。
 *
 * @param path    文件相对路径，不能为空
 * @param content 新的文件内容，为 {@code null} 时清空文件
 */
public record UpdateFileRequest(
        @NotBlank(message = "文件路径不能为空")
        String path,

        String content) {
}
