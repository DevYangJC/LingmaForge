package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 需求分析后生成的结构化执行计划。
 *
 * @param framework       前端框架类型（react-vite-ts / vue-vite-ts）
 * @param packageManager  包管理器（npm / yarn / pnpm）
 * @param files           待生成文件计划列表
 * @param generationOrder 文件生成顺序（按依赖拓扑排序）
 * @param buildCommands   构建与启动命令列表
 */
public record PlanResult(
        String framework,
        String packageManager,
        List<FilePlan> files,
        List<String> generationOrder,
        List<String> buildCommands) implements Serializable {

    private static final long serialVersionUID = 1L;
}
