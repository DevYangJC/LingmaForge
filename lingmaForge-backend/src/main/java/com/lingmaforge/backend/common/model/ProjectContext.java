package com.lingmaforge.backend.common.model;

import java.io.Serializable;
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
        List<String> dependencies) implements Serializable {

    private static final long serialVersionUID = 1L;
}
