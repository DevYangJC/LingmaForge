package com.lingmaforge.backend.ai.tool;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lingmaforge.backend.ai.observer.GenerationContext;
import com.lingmaforge.backend.service.ProjectFileService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 迭代修改工具集，归属迭代修改 Agent。
 *
 * <p>包含一个 @Tool 方法 {@link #searchCode(String, List)}，用于在文件中搜索关键词、
 * 定位需要修改的代码片段。</p>
 */
@Component
public class IterationTools {

    private final ProjectFileService projectFileService;

    public IterationTools(ProjectFileService projectFileService) {
        this.projectFileService = projectFileService;
    }

    /**
     * 在项目文件中搜索包含指定关键词的代码片段，返回匹配行及上下文。
     *
     * @param keyword   搜索关键词
     * @param filePaths 待搜索的文件路径列表
     * @return 匹配结果文本，含文件名、行号与上下文
     */
    @Tool("在项目文件中搜索包含指定关键词的代码片段，返回匹配行及上下文。用于迭代修改时定位需要修改的代码")
    public String searchCode(
            @P("搜索关键词") String keyword,
            @P("待搜索的文件相对路径列表") List<String> filePaths) {
        Long projectId = GenerationContext.get().projectId();
        StringBuilder results = new StringBuilder();
        for (String path : filePaths) {
            String content = projectFileService.readFile(projectId, path);
            if (content == null) {
                continue;
            }
            List<String> lines = content.lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(keyword)) {
                    int start = Math.max(0, i - 2);
                    int end = Math.min(lines.size(), i + 3);
                    results.append("--- 文件: ").append(path).append(" 行 ").append(i + 1).append(" ---\n");
                    for (int j = start; j < end; j++) {
                        results.append(j == i ? ">>> " : "    ");
                        results.append(lines.get(j)).append("\n");
                    }
                    results.append("\n");
                }
            }
        }
        return results.isEmpty() ? "未找到包含关键词的代码: " + keyword : results.toString();
    }
}
