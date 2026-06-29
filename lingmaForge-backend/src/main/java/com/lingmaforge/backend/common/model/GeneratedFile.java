package com.lingmaforge.backend.common.model;

/**
 * 生成文件的内容与元数据。
 *
 * @param path      文件相对路径
 * @param content   文件内容
 * @param checksum  内容校验和，用于增量更新时比对变更
 * @param generated 是否由 AI 生成（{@code false} 表示是用户手动创建）
 */
public record GeneratedFile(
        String path,
        String content,
        String checksum,
        boolean generated) {
}
