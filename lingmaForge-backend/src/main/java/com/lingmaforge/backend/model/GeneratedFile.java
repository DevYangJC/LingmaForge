package com.lingmaforge.backend.model;

/**
 * 生成文件的内容与元数据。
 */
public record GeneratedFile(
        String path,
        String content,
        String checksum,
        boolean generated) {
}
