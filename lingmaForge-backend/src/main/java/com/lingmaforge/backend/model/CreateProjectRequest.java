package com.lingmaforge.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建项目时使用的请求体。
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
