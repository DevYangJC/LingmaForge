package com.lingmaforge.backend.ai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.ai.observer.GenerationContext;
import com.lingmaforge.backend.model.Patch;
import com.lingmaforge.backend.service.ProjectFileService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 文件操作工具集，归属代码生成 / 样式优化 / 迭代修改 Agent。
 *
 * <p>包含三个 @Tool 方法：
 * <ul>
 *   <li>{@link #writeFile(String, String)} —— 写入完整文件（磁盘 + 数据库双写）</li>
 *   <li>{@link #patchFile(String, List)} —— 增量修改文件（只改指定行）</li>
 *   <li>{@link #validateCode(String, String)} —— 校验代码质量（import 路径、export、类型）</li>
 * </ul>
 * 当前项目 ID 与 SSE 发射器通过 {@link GenerationContext} 的 ThreadLocal 获取。</p>
 */
@Component
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);

    /** 匹配 ES6 / TS import 语句中的模块来源。 */
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("import\\s+[^;]*?from\\s+['\"]([^'\"]+)['\"]");

    private static final List<String> EXTERNAL_PREFIXES = List.of("react", "react-", "@", "vue", "axios");

    private final ProjectFileService projectFileService;

    public FileTools(ProjectFileService projectFileService) {
        this.projectFileService = projectFileService;
    }

    /**
     * 将生成的代码内容写入项目文件，同时更新数据库并推送 SSE 事件。
     *
     * @param path    文件相对路径，如 src/components/PlanCard.tsx
     * @param content 文件内容
     * @return 写入结果描述（含行数），供 Agent 判断是否写入成功
     */
    @Tool("将生成的代码内容写入项目文件，同时更新数据库并推送 SSE 事件。参数 path 为相对路径，content 为完整文件内容")
    public String writeFile(
            @P("文件相对路径，例如 src/components/PlanCard.tsx") String path,
            @P("文件的完整内容") String content) {

        // ★★★ 日志：让大家看到大模型传过来的 content 到底长什么样 ★★★
        log.info("""
                ╔══════════════════════════════════════════════════════════╗
                ║  writeFile 被大模型调用！                                 ║
                ╠══════════════════════════════════════════════════════════╣
                ║  path  = {}
                ╠══════════════════════════════════════════════════════════╣
                ║  content 的前 3 行 →
                ║  {}
                ║  content 是否以 ``` 开头? → {}
                ║  content 是否包含 "好的"? → {}
                ║  content 总行数 → {}
                ╚══════════════════════════════════════════════════════════╝
                """,
                path,
                content.lines().limit(3).toList(),
                content.trim().startsWith("```"),
                content.contains("好的") || content.contains("以下是"),
                content.lines().count());

        Long projectId = GenerationContext.get().projectId();
        int lines = projectFileService.writeFile(projectId, path, content, "new");
        GenerationContext.get().emitter().emitFile(path, content, "new");
        return "文件写入成功: " + path + "（" + lines + " 行）";
    }

    /**
     * 对项目文件做增量修改（diff patch），只修改指定行，不重写整个文件。
     *
     * @param path    文件相对路径
     * @param patches 补丁列表，每个补丁指定行号、旧行内容、新行内容
     * @return 应用结果描述
     */
    @Tool("对项目文件做增量修改，只修改指定行，不重写整个文件。用于样式优化和迭代修改场景，保留用户手动修改")
    public String patchFile(
            @P("文件相对路径") String path,
            @P("补丁列表，每个补丁含 line(行号)、old(旧行)、newContent(新行)") List<Patch> patches) {
        Long projectId = GenerationContext.get().projectId();
        if (patches == null || patches.isEmpty()) {
            return "未提供补丁，文件未修改: " + path;
        }
        int applied = projectFileService.patchFile(projectId, path, patches);
        String content = projectFileService.readFile(projectId, path);
        GenerationContext.get().emitter().emitFile(path, content, "modified");
        return "样式/迭代优化完成: " + applied + " 处修改已应用到 " + path;
    }

    /**
     * 验证生成的代码质量：检查 import 路径、export 声明、TypeScript 类型完整性。
     *
     * @param path    文件相对路径
     * @param content 文件内容
     * @return 验证结果，通过返回"验证通过"，否则返回错误清单
     */
    @Tool("验证生成的代码质量，检查 import 路径是否存在、export 声明、TypeScript any 类型。返回验证结果供 Agent 决定是否修正")
    public String validateCode(
            @P("文件相对路径") String path,
            @P("待校验的文件内容") String content) {
        List<String> errors = new ArrayList<>();

        List<String> imports = extractImports(content);
        List<String> existing = projectFileService.listFilePaths(GenerationContext.get().projectId());
        for (String imp : imports) {
            if (!isExternalPackage(imp) && !existsInProject(imp, path, existing)) {
                errors.add("import 路径不存在: " + imp);
            }
        }

        if (!content.contains("export")) {
            errors.add("文件缺少 export 声明，其他文件无法引用此组件");
        }

        if (content.contains(": any") || content.contains("as any")) {
            errors.add("使用了 any 类型，请使用具体类型替代");
        }

        if (errors.isEmpty()) {
            return "代码验证通过，质量良好。";
        }
        return "代码验证失败:\n" + String.join("\n", errors) + "\n请修正后重新调用 writeFile。";
    }

    private List<String> extractImports(String content) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    private boolean isExternalPackage(String module) {
        return EXTERNAL_PREFIXES.stream().anyMatch(module::startsWith);
    }

    private boolean existsInProject(String module, String currentPath, List<String> existing) {
        if (existing.contains(module)) {
            return true;
        }
        // 解析相对路径：以当前文件所在目录为基准
        String dir = currentPath.contains("/") ? currentPath.substring(0, currentPath.lastIndexOf('/')) : "";
        String resolved = resolveRelative(dir, module);
        if (existing.contains(resolved)) {
            return true;
        }
        // 尝试常见扩展名补全
        for (String ext : List.of(".tsx", ".ts", ".css", ".json")) {
            if (existing.contains(resolved + ext) || existing.contains(resolved + "/index" + ext)) {
                return true;
            }
        }
        return false;
    }

    private String resolveRelative(String dir, String module) {
        if (!module.startsWith(".")) {
            return module;
        }
        if (dir.isEmpty()) {
            return module.substring(2);
        }
        return dir + "/" + module.substring(2);
    }
}
