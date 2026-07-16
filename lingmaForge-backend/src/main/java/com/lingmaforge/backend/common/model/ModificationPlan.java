package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 本轮迭代修改的结构化执行计划。
 *
 * @param summary 修改计划的中文摘要
 * @param changes 待执行的文件变更列表
 * @param risks 执行前识别到的风险或注意事项
 */
public record ModificationPlan(
        String summary,
        List<FileChangePlan> changes,
        List<String> risks) implements Serializable {

    private static final long serialVersionUID = 1L;
}