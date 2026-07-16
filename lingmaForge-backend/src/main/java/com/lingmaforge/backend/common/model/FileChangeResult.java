package com.lingmaforge.backend.common.model;

import java.io.Serializable;

/**
 * 单个文件变更的执行结果。
 *
 * @param path 文件相对项目根目录的路径
 * @param action 已执行的动作，取值为 create、update、delete
 * @param success 是否执行成功
 * @param message 执行结果说明或失败原因
 */
public record FileChangeResult(
        String path,
        String action,
        boolean success,
        String message) implements Serializable {

    private static final long serialVersionUID = 1L;
}