package com.lingmaforge.backend.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建项目时使用的请求体。
 *
 * @param name        项目名称，最长 80 个字符
 * @param description 项目描述，最长 500 个字符，可选
 * @param framework   前端框架类型，支持 {@code react-vite-ts} 或 {@code vue-vite-ts}
 */
public record CreateProjectRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 80, message = "项目名称不能超过 80 个字符")
        String name,

        @Size(max = 500, message = "项目描述不能超过 500 个字符")
        String description,

        @Pattern(regexp = "react-vite-ts|vue-vite-ts", message = "框架类型仅支持 react-vite-ts 或 vue-vite-ts")
        String framework) {
}
