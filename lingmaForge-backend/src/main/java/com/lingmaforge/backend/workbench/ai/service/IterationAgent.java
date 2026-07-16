package com.lingmaforge.backend.workbench.ai.service;

import com.lingmaforge.backend.common.model.BuildErrorAnalysis;
import com.lingmaforge.backend.common.model.IterationIntent;
import com.lingmaforge.backend.common.model.ModificationPlan;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 迭代修改 Agent 的 AiServices 接口契约。
 *
 * <p>该 Agent 面向已有 Vue 项目的对话式修改场景：先识别修改意图，再生成文件级修改计划，
 * 必要时根据构建错误分析结果进入下一轮定向修复。</p>
 */
public interface IterationAgent {

    /**
     * 分析用户本轮对话式修改的意图。
     *
     * @param prompt 用户输入的修改指令
     * @param projectContext 当前项目上下文摘要
     * @return 结构化意图识别结果
     */
    @UserMessage("""
            用户修改请求：{{prompt}}

            项目上下文：
            {{projectContext}}

            请返回符合 IterationIntent 字段的 JSON：type、summary、targetFiles、requiresBuild。
            """)
    IterationIntent analyzeIntent(@V("prompt") String prompt, @V("projectContext") String projectContext);

    /**
     * 根据用户指令、项目上下文和意图识别结果生成文件级修改计划。
     *
     * @param prompt 用户输入的修改指令
     * @param projectContext 当前项目上下文摘要
     * @param intent 意图识别结果
     * @return 结构化文件修改计划
     */
    default ModificationPlan planModification(String prompt, String projectContext, IterationIntent intent) {
        return planModification(prompt, projectContext, intent, null);
    }

    /**
     * 根据用户指令、项目上下文、意图识别结果和构建错误分析生成文件级修改计划。
     *
     * @param prompt 用户输入的修改指令
     * @param projectContext 当前项目上下文摘要
     * @param intent 意图识别结果
     * @param buildErrorAnalysis 上一轮构建失败后的分析结果；首次规划时为空
     * @return 结构化文件修改计划
     */
    @UserMessage("""
            用户修改请求：
            {{prompt}}

            项目上下文：
            {{projectContext}}

            意图识别结果：
            {{intent}}

            构建错误分析：
            {{buildErrorAnalysis}}

            请返回符合 ModificationPlan 字段的 JSON：summary、changes、risks。
            changes 中每一项包含 path、action、reason、newContent，action 只能是 create、update、delete。
            如果构建错误分析不为空，本轮计划必须优先修复 suspectedFiles 和 suggestedFix 指向的问题。
            """)
    ModificationPlan planModification(
            @V("prompt") String prompt,
            @V("projectContext") String projectContext,
            @V("intent") IterationIntent intent,
            @V("buildErrorAnalysis") BuildErrorAnalysis buildErrorAnalysis);

    /**
     * 分析构建日志并给出下一轮定向修复建议。
     *
     * @param buildLog 构建失败日志
     * @param plan 本轮修改计划
     * @return 结构化构建错误分析结果
     */
    @UserMessage("""
            构建日志：
            {{buildLog}}

            本轮修改计划：
            {{plan}}

            请返回符合 BuildErrorAnalysis 字段的 JSON：category、summary、suspectedFiles、suggestedFix。
            """)
    BuildErrorAnalysis analyzeBuildError(@V("buildLog") String buildLog, @V("plan") ModificationPlan plan);
}