package com.lingmaforge.backend.model;

import java.util.List;

/**
 * 单个生成文件的计划。
 */
public record FilePlan(
        String path,
        String purpose,
        String fileType,
        List<String> dependencies,
        boolean required) {
}
