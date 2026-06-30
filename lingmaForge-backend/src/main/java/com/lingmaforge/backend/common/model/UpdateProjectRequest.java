package com.lingmaforge.backend.common.model;

import java.io.Serializable;

import jakarta.validation.constraints.Size;

/**
 * 更新项目元数据的请求体。
 *
 * <p>所有字段均为可选，传 null 的字段保持原值不变。</p>
 *
 * @param name        项目名称，最长 80 个字符，可选
 * @param description 项目描述，最长 500 个字符，可选
 */
public record UpdateProjectRequest(
        @Size(max = 80, message = "项目名称不能超过 80 个字符")
        String name,

        @Size(max = 500, message = "项目描述不能超过 500 个字符")
        String description) implements Serializable {

    private static final long serialVersionUID = 1L;
}
