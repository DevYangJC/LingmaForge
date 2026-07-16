package com.lingmaforge.backend.workbench.ai.plan;

import java.util.List;

/**
 * 计划中的单个任务项——由 LLM 通过 updatePlan 工具回传。
 *
 * @param id     任务序号（从 1 开始）
 * @param text   任务描述
 * @param status 当前状态：pending / in_progress / completed
 * @param deps   前置依赖的任务 id 列表
 */
public record PlanItem(
        int id,
        String text,
        String status,
        List<Object> deps) {
}