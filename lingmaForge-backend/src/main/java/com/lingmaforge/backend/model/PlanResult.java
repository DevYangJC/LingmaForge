package com.lingmaforge.backend.model;

import java.util.List;

/**
 * 需求分析后生成的结构化执行计划。
 */
public record PlanResult(
        String framework,
        String packageManager,
        List<FilePlan> files,
        List<String> generationOrder,
        List<String> buildCommands) {
}
