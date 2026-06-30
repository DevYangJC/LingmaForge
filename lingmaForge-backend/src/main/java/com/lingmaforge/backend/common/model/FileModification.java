package com.lingmaforge.backend.common.model;

import java.io.Serializable;

/**
 * SSE 迭代修改事件中的文件变更记录，用于前端 diff 展示。
 *
 * @param path            文件相对路径
 * @param originalContent 修改前的内容
 * @param newContent      修改后的内容
 */
public record FileModification(
        String path,
        String originalContent,
        String newContent) implements Serializable {

    private static final long serialVersionUID = 1L;
}
