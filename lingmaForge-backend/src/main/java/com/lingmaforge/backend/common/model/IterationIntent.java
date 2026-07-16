package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 用户本轮对话式修改的意图识别结果。
 *
 * @param type 修改类型，例如 style、feature、bugfix、refactor、dependency、unknown
 * @param summary 用户意图的简短中文摘要
 * @param targetFiles 可能需要读取或修改的文件路径列表
 * @param requiresBuild 修改完成后是否需要触发构建验证
 */
public record IterationIntent(
        String type,
        String summary,
        List<String> targetFiles,
        boolean requiresBuild) implements Serializable {

    private static final long serialVersionUID = 1L;
}