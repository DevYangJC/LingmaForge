package com.lingmaforge.backend.common.model;

import java.util.List;

/**
 * 项目上下文快照，供需求分析 / 代码生成 Agent 读取。
 *
 * @param framework       框架类型
 * @param filePaths       已有文件相对路径列表
 * @param dependencies    package.json 中的依赖列表
 */
public record ProjectContext(
        String framework,
        List<String> filePaths,
        List<String> dependencies) {
}
