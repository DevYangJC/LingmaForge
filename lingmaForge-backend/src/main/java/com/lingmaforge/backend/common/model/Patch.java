package com.lingmaforge.backend.common.model;

/**
 * 文件增量修改补丁。
 *
 * <p>用于 patchFile 工具，只修改指定行，不重写整个文件，保留用户手动修改。</p>
 *
 * @param line 目标行号（1-based）
 * @param old  期望的旧行内容（用于校验，匹配后才替换）
 * @param newContent 新行内容
 */
public record Patch(int line, String old, String newContent) {
}
