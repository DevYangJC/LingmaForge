package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 构建失败后的错误分析结果。
 *
 * @param category 错误分类，例如 syntax、dependency、type、config、unknown
 * @param summary 错误原因的中文摘要
 * @param suspectedFiles 可能导致错误的文件路径列表
 * @param suggestedFix 建议下一轮修复采用的策略
 */
public record BuildErrorAnalysis(
        String category,
        String summary,
        List<String> suspectedFiles,
        String suggestedFix) implements Serializable {

    private static final long serialVersionUID = 1L;
}