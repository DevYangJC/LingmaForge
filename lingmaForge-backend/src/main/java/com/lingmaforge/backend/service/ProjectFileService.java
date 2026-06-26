package com.lingmaforge.backend.service;

import java.util.List;
import java.util.Map;

import com.lingmaforge.backend.model.FileNode;
import com.lingmaforge.backend.model.Patch;

/**
 * 项目文件服务契约。
 *
 * <p>负责项目文件的双写（磁盘 + 数据库）、读取、文件树构建与增量补丁。</p>
 */
public interface ProjectFileService {

    /**
     * 写入文件（磁盘 + 数据库双写）。
     *
     * @param projectId 项目 ID
     * @param path      文件相对路径
     * @param content   文件内容
     * @param status    文件状态：new / modified / unchanged
     * @return 写入的文件行数
     */
    int writeFile(Long projectId, String path, String content, String status);

    /**
     * 从数据库读取文件内容。
     *
     * @param projectId 项目 ID
     * @param path      文件相对路径
     * @return 文件内容，不存在返回 null
     */
    String readFile(Long projectId, String path);

    /**
     * 批量读取文件内容，未生成的文件返回 null。
     *
     * @param projectId 项目 ID
     * @param paths     文件相对路径列表
     * @return 路径到内容的映射
     */
    Map<String, String> readFiles(Long projectId, List<String> paths);

    /**
     * 构建项目文件树。
     *
     * @param projectId 项目 ID
     * @return 文件树根节点列表
     */
    List<FileNode> getFileTree(Long projectId);

    /**
     * 对文件应用增量补丁，只修改匹配的行。
     *
     * @param projectId 项目 ID
     * @param path      文件相对路径
     * @param patches   补丁列表
     * @return 成功应用的补丁数量
     */
    int patchFile(Long projectId, String path, List<Patch> patches);

    /**
     * 查询项目下所有文件路径。
     *
     * @param projectId 项目 ID
     * @return 文件路径列表
     */
    List<String> listFilePaths(Long projectId);
}
