package com.lingmaforge.backend.common.model;

import java.util.List;

/**
 * 文件树节点，用于前端文件树面板渲染。
 *
 * @param name     节点名称（文件名或目录名）
 * @param path     相对项目根目录的路径
 * @param type     节点类型：dir / file
 * @param language 文件语言（用于编辑器高亮），目录节点为 null
 * @param status   文件状态：new / modified / unchanged，目录节点为 null
 * @param children 子节点列表，仅目录节点非空
 */
public record FileNode(
        String name,
        String path,
        String type,
        String language,
        String status,
        List<FileNode> children) {
}
