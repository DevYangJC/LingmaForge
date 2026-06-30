package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 单个生成文件的计划。
 *
 * @param path         文件相对路径
 * @param purpose      该文件在项目中的作用说明
 * @param fileType     文件类型（如 component / style / config / util）
 * @param dependencies 该文件依赖的其他文件路径列表
 * @param required     是否为必需文件（项目运行不可或缺）
 */
public record FilePlan(
        String path,
        String purpose,
        String fileType,
        List<String> dependencies,
        boolean required) implements Serializable {

    private static final long serialVersionUID = 1L;
}
