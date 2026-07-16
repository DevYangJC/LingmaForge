package com.lingmaforge.backend.workbench.ai.support;

import java.util.List;

/**
 * Vue 模板文件边界策略。
 *
 * <p>首次生成、对话式迭代和上下文加载都必须使用同一套模板边界，避免不同节点各自维护列表导致
 * 基础脚手架被模型误改，或者上下文缺少关键入口文件。</p>
 */
public final class TemplateFilePolicy {

    private static final List<String> PROTECTED_TEMPLATE_FILES = List.of(
            "package.json",
            "index.html",
            "vite.config.ts",
            "tsconfig.json",
            "tsconfig.app.json",
            "tsconfig.node.json",
            "env.d.ts",
            "eslint.config.ts",
            ".editorconfig",
            ".gitattributes",
            ".gitignore",
            ".oxlintrc.json",
            ".prettierrc.json",
            "src/main.ts",
            "public/favicon.ico");

    private static final List<String> VUE_CONTEXT_ANCHOR_FILES = List.of(
            "package.json",
            "src/main.ts",
            "src/App.vue",
            "src/router/index.ts");

    private TemplateFilePolicy() {
    }

    /**
     * 判断文件是否属于 Vue 模板基础骨架。
     *
     * @param path 项目内相对路径
     * @return 如果文件应由模板或人工流程维护，返回 true
     */
    public static boolean isProtectedTemplateFile(String path) {
        return path != null && (PROTECTED_TEMPLATE_FILES.contains(path) || path.startsWith(".vscode/"));
    }

    /**
     * 返回 Vue 项目迭代时必须加载的上下文锚点文件。
     *
     * @return 模板关键文件路径列表
     */
    public static List<String> vueContextAnchorFiles() {
        return VUE_CONTEXT_ANCHOR_FILES;
    }
}