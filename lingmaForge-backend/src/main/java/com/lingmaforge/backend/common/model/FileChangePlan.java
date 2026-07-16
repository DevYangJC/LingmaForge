package com.lingmaforge.backend.common.model;

import java.io.Serializable;

/**
 * 单个文件的计划变更项。
 *
 * @param path 文件相对项目根目录的路径
 * @param action 变更动作，取值为 create、update、delete
 * @param reason 执行该变更的原因说明
 * @param newContent create/update 时的新文件完整内容，delete 时为空字符串
 */
public record FileChangePlan(
        String path,
        String action,
        String reason,
        String newContent) implements Serializable {

    private static final long serialVersionUID = 1L;
}