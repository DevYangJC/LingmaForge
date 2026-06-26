package com.lingmaforge.backend.ai.node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.ai.factory.AgentFactory;
import com.lingmaforge.backend.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.ai.service.CodeGenAgent;
import com.lingmaforge.backend.model.FilePlan;
import com.lingmaforge.backend.model.PlanResult;
import com.lingmaforge.backend.model.RequirementSpec;
import com.lingmaforge.backend.service.ProjectFileService;
import com.lingmaforge.backend.service.PromptTemplateLoader;

/**
 * 节点三：代码生成（核心）。
 *
 * <p>遍历执行规划中的文件清单，逐文件生成代码。采用方案 B：外部循环逐文件调用，
 * 单次调用内由 {@link CodeGenAgent} 驱动 Agent 循环（readFileContext → 生成 → writeFile）。</p>
 *
 * <p>构建失败回退到本节点时，状态中的 buildError 会注入提示词，驱动 Agent 修复相关文件。</p>
 *
 * <p>模型：Claude Sonnet 4；当前实现复用单一 ChatModel Bean。</p>
 */
@Component
public class CodeGenerationNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "code_generation";

    private final CodeGenAgent agent;
    private final PromptTemplateLoader promptLoader;
    private final ProjectFileService projectFileService;
    private final ObjectMapper objectMapper;

    public CodeGenerationNode(AgentFactory agentFactory,
            GenerationStreamRegistry streamRegistry,
            PromptTemplateLoader promptLoader,
            ProjectFileService projectFileService,
            ObjectMapper objectMapper) {
        super(streamRegistry);
        this.agent = agentFactory.createCodeGenAgent();
        this.promptLoader = promptLoader;
        this.projectFileService = projectFileService;
        this.objectMapper = objectMapper;
    }

    /**
     * 逐文件生成代码。
     *
     * @param state 流水线状态
     * @return 状态更新：写入已生成文件列表（追加）与当前文件序号
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state);
        Long projectId = projectId(state);
        try {
            RequirementSpec analysisResult = state.analysisResult().orElseThrow();
            PlanResult planResult = state.planResult().orElseThrow();
            String buildError = state.buildError().orElse(null);
            int retryCount = state.retryCount().orElse(0);

            if (buildError != null) {
                emitter.emitNode(NODE_NAME,
                        "构建失败，正在修复代码（第 " + retryCount + " 次重试）...", "TEXT");
            } else {
                emitter.emitNode(NODE_NAME, "开始逐文件生成代码...", "TEXT");
            }

            List<FilePlan> files = planResult.files();
            for (int i = 0; i < files.size(); i++) {
                FilePlan filePlan = files.get(i);
                if (buildError != null && !shouldRegenerate(filePlan, buildError, projectId)) {
                    continue;
                }
                generateOneFile(state, analysisResult, filePlan, buildError, projectId, emitter);
                emitter.emitNode(NODE_NAME,
                        "已生成 " + (i + 1) + "/" + files.size() + ": " + filePlan.path(), "TEXT");
            }
            // 全量重新生成后清空构建错误，避免再次回退时误判
            Map<String, Object> updates = new HashMap<>();
            updates.put(CodeGenState.BUILD_ERROR, null);
            return updates;
        } catch (Exception e) {
            log.error("[{}] 代码生成失败", state.taskId().orElse(""), e);
            emitter.emitNode(NODE_NAME, "代码生成失败: " + e.getMessage(), "TEXT");
            throw e;
        } finally {
            clearContext();
        }
    }

    private void generateOneFile(CodeGenState state, RequirementSpec analysisResult,
            FilePlan filePlan, String buildError, Long projectId, GenerationStreamEmitter emitter) {
        Map<String, String> variables = new HashMap<>();
        variables.put("appName", analysisResult.appName());
        variables.put("description", analysisResult.description() == null ? "" : analysisResult.description());
        variables.put("filePath", filePlan.path());
        variables.put("fileType", filePlan.fileType() == null ? "" : filePlan.fileType());
        variables.put("fileDescription", filePlan.purpose() == null ? "" : filePlan.purpose());
        variables.put("fileContext", collectDependencyContext(projectId, filePlan.dependencies()));
        variables.put("analysisResult", toJson(analysisResult));
        variables.put("buildError", buildError == null ? "无" : buildError);

        String prompt = promptLoader.loadUserPrompt("code-generation", variables);
        log.info("[{}] 生成文件: {}", state.taskId().orElse(""), filePlan.path());
        agent.generate(prompt);
    }

    private String collectDependencyContext(Long projectId, List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return "（无依赖文件）";
        }
        Map<String, String> contents = projectFileService.readFiles(projectId, dependencies);
        StringBuilder context = new StringBuilder();
        for (String dep : dependencies) {
            String content = contents.get(dep);
            if (content != null) {
                context.append("--- 文件: ").append(dep).append(" ---\n").append(content).append("\n\n");
            }
        }
        return context.toString();
    }

    /**
     * 构建失败回退时，只重新生成与错误相关的文件，避免全量重做。
     *
     * <p>当前策略：错误信息中包含文件路径则重生成该文件；无明确路径时全量重生成。</p>
     */
    private boolean shouldRegenerate(FilePlan filePlan, String buildError, Long projectId) {
        if (buildError == null) {
            return true;
        }
        boolean anyPathMentioned = filePlan.path() != null && buildError.contains(filePlan.path());
        boolean noPathInError = projectFileService.listFilePaths(projectId).stream()
                .noneMatch(buildError::contains);
        return anyPathMentioned || noPathInError;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
