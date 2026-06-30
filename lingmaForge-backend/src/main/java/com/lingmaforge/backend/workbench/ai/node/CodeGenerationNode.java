package com.lingmaforge.backend.workbench.ai.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.CodeGenAgent;
import com.lingmaforge.backend.common.model.FilePlan;
import com.lingmaforge.backend.common.model.GeneratedFile;
import com.lingmaforge.backend.common.model.PlanResult;
import com.lingmaforge.backend.common.model.RequirementSpec;
import com.lingmaforge.backend.infra.config.AsyncConfig;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import com.lingmaforge.backend.workbench.service.PromptTemplateLoader;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.ResultCode;

/**
 * 节点三：代码生成（核心）。
 *
 * <p>基于 {@link CompletableFuture} 和流式大模型 {@link CodeGenAgent} 实现多文件并行流式生成。
 * 每一个文件生成时，将 Token 逐字推送至前端以呈现 Monaco 打字机效果，流式结束后由 Java 写入磁盘并注销。</p>
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
    private final Executor executor;

    public CodeGenerationNode(AgentFactory agentFactory,
            GenerationStreamRegistry streamRegistry,
            PromptTemplateLoader promptLoader,
            ProjectFileService projectFileService,
            ObjectMapper objectMapper,
            @Qualifier(AsyncConfig.FILE_GEN_EXECUTOR) Executor executor) {
        super(streamRegistry);
        this.agent = agentFactory.createCodeGenAgent();
        this.promptLoader = promptLoader;
        this.projectFileService = projectFileService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    /**
     * 并行流式生成代码。
     *
     * @param state 流水线状态
     * @return 状态更新
     */
    public Map<String, Object> execute(CodeGenState state) {
        // 使用带有节点名和展示标题的方法初始化，这会自动广播 node_start 事件
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "开始并行生成代码...");
        Long projectId = projectId(state);
        try {
            RequirementSpec analysisResult = state.analysisResult().orElseThrow();
            PlanResult planResult = state.planResult().orElseThrow();
            String buildError = state.buildError().orElse(null);
            int retryCount = state.retryCount().orElse(0);

            if (buildError != null) {
                emitter.emitNode(NODE_NAME,
                        "构建失败，正在修复代码（第 " + retryCount + " 次重试）...", "TEXT");
            }

            List<FilePlan> filePlans = planResult.files();
            String taskId = state.taskId().orElse("");

            // 1. 构建并行 CompletableFuture 任务列表
            //    出现构建错误时，只重新生成与构建错误相关的文件
            List<FilePlan> filesToGenerate = buildError == null
                    ? filePlans
                    : filePlans.stream()
                        .filter(fp -> shouldRegenerate(fp, buildError, projectId))
                        .toList();

            List<CompletableFuture<Void>> futures = filesToGenerate.stream()
                    .filter(filePlan -> buildError == null || shouldRegenerate(filePlan, buildError, projectId))
                    .map(filePlan -> CompletableFuture.runAsync(() -> {
                        try {
                            // 在子线程中绑定 GenerationContext 上下文，防止并发执行时获取不到连接
                            GenerationContext.set(projectId, taskId, emitter);
                            generateOneFile(state, analysisResult, filePlan, buildError, projectId, emitter);
                        } finally {
                            GenerationContext.clear();
                        }
                    }, executor))
                    .toList();

            // 2. 阻塞并等待所有文件并行流式生成与写入完成
            //    使用超时防止线程池死锁：5 分钟超时后抛出异常，由外层 catch 捕获
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(300, TimeUnit.SECONDS);

            // 3. 构建已生成文件列表供下游节点（StyleOptimizationNode）使用
            List<GeneratedFile> generatedFiles = filesToGenerate.stream()
                    .map(fp -> new GeneratedFile(fp.path(), "", "", true))
                    .toList();
            // 追加之前已生成的文件（以防回退修复时丢失首次生成的文件记录）
            List<GeneratedFile> previousFiles = state.generatedFiles().orElse(List.of());
            List<GeneratedFile> mergedFiles = new ArrayList<>(previousFiles);
            for (GeneratedFile gf : generatedFiles) {
                if (previousFiles.stream().noneMatch(pf -> pf.path().equals(gf.path()))) {
                    mergedFiles.add(gf);
                }
            }

            // 全量重新生成后清空构建错误，避免再次回退时误判
            Map<String, Object> updates = new HashMap<>();
            updates.put(CodeGenState.BUILD_ERROR, null);
            updates.put(CodeGenState.GENERATED_FILES, mergedFiles);
            updates.put(CodeGenState.CURRENT_FILE_INDEX, mergedFiles.size());
            return updates;
        } catch (Exception e) {
            log.error("[{}] 并行代码生成失败", state.taskId().orElse(""), e);
            emitter.emitNode(NODE_NAME, "代码生成失败: " + e.getMessage(), "TEXT");
            throw new RuntimeException("代码生成失败", e);
        } finally {
            // 正常/异常结束时触发广播 node_end 事件并注销上下文
            completeNode(emitter, NODE_NAME);
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
        String taskId = state.taskId().orElse("");
        log.info("[{}] 开始并行流式生成文件: {}", taskId, filePlan.path());

        CompletableFuture<String> streamFuture = new CompletableFuture<>();
        StringBuilder codeBuilder = new StringBuilder();
        final boolean[] stopped = {false};

        // 订阅流式大模型输出并逐 Token 推送给前端
        agent.generate(prompt)
                .onPartialThinking(thinking -> {
                    // 每收到一个 thinking token 时检查停止信号
                    if (getStreamRegistry().isStopRequested(taskId)) {
                        stopped[0] = true;
                        streamFuture.complete(codeBuilder.toString());
                        return;
                    }
                    emitter.emitThinking(NODE_NAME, thinking.text());
                })
                .onPartialResponse(token -> {
                    // 每收到一个代码 token 时检查停止信号
                    if (getStreamRegistry().isStopRequested(taskId)) {
                        stopped[0] = true;
                        streamFuture.complete(codeBuilder.toString());
                        return;
                    }
                    emitter.emitFileToken(filePlan.path(), token);
                    codeBuilder.append(token);
                })
                .onCompleteResponse(chatResponse -> {
                    if (!stopped[0]) {
                        streamFuture.complete(codeBuilder.toString());
                    }
                })
                .onError(error -> {
                    if (!stopped[0]) {
                        streamFuture.completeExceptionally(error);
                    }
                })
                .start();

        try {
            // 阻塞当前文件的线程，直至当前文件流式输送完毕
            String rawCode = streamFuture.join();
            // 如果已收到停止信号，跳过落盘
            if (getStreamRegistry().isStopRequested(taskId)) {
                log.info("[{}] 文件生成因停止信号跳过: {}", taskId, filePlan.path());
                return;
            }
            // 清洗大模型输出：去除 Markdown 包裹和客套话
            String fullCode = cleanupCodeOutput(rawCode);
            // 由 Java 写入物理磁盘与数据库
            projectFileService.writeFile(projectId, filePlan.path(), fullCode, "new");
            // 广播当前文件已完成
            emitter.emitFileComplete(filePlan.path());
            log.info("[{}] 并行流式生成完成并已落盘: {}", taskId, filePlan.path());
        } catch (Exception e) {
            log.error("[{}] 文件并行生成失败: {}", taskId, filePlan.path(), e);
            throw new RuntimeException("生成文件 " + filePlan.path() + " 失败: " + e.getMessage(), e);
        }
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
     * 判断某个文件计划是否应在构建失败后重新生成。
     *
     * <p>回退策略（从激进到保守）：
     * <ol>
     *   <li>构建错误中明确包含此文件路径 → 必须重新生成</li>
     *   <li>构建错误不包含任何项目文件路径（模块级错误，如缺失依赖包） → 只重新生成配置文件与入口文件</li>
     *   <li>构建错误包含其他文件路径但不包含此文件 → 不重新生成（无关联）</li>
     * </ol></p>
     */
    private boolean shouldRegenerate(FilePlan filePlan, String buildError, Long projectId) {
        if (buildError == null) {
            return true;
        }
        boolean pathMentioned = filePlan.path() != null && buildError.contains(filePlan.path());
        if (pathMentioned) {
            return true;
        }
        // 检查构建错误是否提及任何已存在的项目文件路径
        List<String> existingPaths = projectFileService.listFilePaths(projectId);
        boolean anyFileMentioned = existingPaths.stream().anyMatch(buildError::contains);
        if (!anyFileMentioned) {
            // 未提及任何具体文件：模块级/全局错误
            // 只重新生成配置文件（package.json、vite.config 等）和入口文件
            String path = filePlan.path();
            return path != null && (path.endsWith("package.json")
                    || path.contains("vite.config")
                    || path.contains("tsconfig")
                    || path.endsWith(".eslintrc")
                    || path.endsWith("App.tsx") || path.endsWith("App.vue")
                    || path.endsWith("main.ts") || path.endsWith("main.tsx")
                    || path.endsWith("index.ts") || path.endsWith("index.html"));
        }
        return false;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 清理大模型输出中的 Markdown 包裹、JSON 信封和客套话，确保写入文件的是纯代码。
     *
     * <p>处理三种常见污染模式：</p>
     * <ol>
     *   <li>Markdown 包裹：```tsx ... ```</li>
     *   <li>JSON 信封：{{"path":"...","content":"真正的代码","status":"new"}} 或 {{"content":"真正的代码"}}</li>
     *   <li>客套话："好的，以下是xxx的代码..."</li>
     * </ol>
     *
     * @param raw 大模型原始输出
     * @return 清理后的纯代码
     */
    static String cleanupCodeOutput(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String cleaned = raw.stripLeading();

        // 1. 检测 JSON 信封：整个输出是一个 JSON 对象，且包含 "content" 字段
        //    这种情况发生在模型试图模拟工具调用格式时
        cleaned = unwrapJsonEnvelope(cleaned);

        // 2. 如果以 Markdown 代码块开头（```tsx / ```typescript / ```css / ``` 等），去掉首行
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }

        // 3. 如果末尾有闭合的 ```，去掉
        int lastFence = cleaned.lastIndexOf("```");
        if (lastFence != -1) {
            cleaned = cleaned.substring(0, lastFence).stripTrailing();
        }

        // 4. 去掉开头的客套话行（包含"好的"、"以下是"、"让我"等常见开头）
        //    持续去掉非代码行，直到遇到含有效代码字符的行
        String[] lines = cleaned.split("\n", -1);
        int codeStart = 0;
        for (int i = 0; i < Math.min(lines.length, 10); i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) continue;
            // 如果这一行看起来像代码（含 import/export/const/function/interface/class 等关键字）
            // 或者是 { < > 等代码符号开头），停止跳过
            if (trimmed.matches("^(import|export|const|let|var|function|interface|type|class|enum|from|declare|namespace|module|public|private|protected|static|abstract|@|\\{|\\[|<|/\\*|//|<!--).*")
                    || trimmed.startsWith("#")) {
                codeStart = i;
                break;
            }
            // 常见客套话关键词
            if (trimmed.matches(".*[好让为请您我我们这].*") && trimmed.length() < 30) {
                codeStart = i + 1;
            } else {
                // 非客套话且非代码行则停止跳过（通常是注释说明）
                if (trimmed.startsWith("/*") || trimmed.startsWith("//") || trimmed.startsWith("<!--")) {
                    codeStart = i;
                    break;
                }
                codeStart = i;
                break;
            }
        }
        if (codeStart > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = codeStart; i < lines.length; i++) {
                if (i > codeStart) sb.append('\n');
                sb.append(lines[i]);
            }
            cleaned = sb.toString();
        }

        return cleaned.stripTrailing();
    }

    /**
     * 检测并解包 JSON 信封格式。
     *
     * <p>某些大模型会输出类似 writeFile 工具调用的 JSON 包裹：
     * <pre>{"path": "tsconfig.node.json", "content": "{\n  \"compilerOptions\": {...}}", "status": "new"}</pre>
     * 这种格式下，实际文件内容在 "content" 字段的值中（可能还被 JSON 转义了）。
     * 如果是这种情况，提取 content 值并进行 JSON 反转义。</p>
     */
    private static String unwrapJsonEnvelope(String text) {
        String trimmed = text.stripLeading();
        if (!trimmed.startsWith("{")) return text;

        try {
            // 只支持 Jackson 或简单 Gson 风格解析；这里用简单字符串检测避免引入依赖
            // 检测是否包含 "content" 字段（writeFile 信封装载签名）
            if (!trimmed.contains("\"content\"")) return text;

            // 尝试用简单字符串提取 content 值
            // 查找 "content": " 之后的内容
            int contentKeyIndex = findJsonKey(trimmed, "content");
            if (contentKeyIndex < 0) return text;

            // 找到 "content": " 后的字符串值起始位置
            int valueStart = trimmed.indexOf('"', contentKeyIndex + 10);
            if (valueStart < 0) return text;
            valueStart = valueStart + 1; // 跳过 "content": 后的第一个引号

            // 查找匹配的结束引号（处理转义）
            StringBuilder content = new StringBuilder();
            boolean escaped = false;
            int i = valueStart;
            for (; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (escaped) {
                    content.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break; // 找到结束引号
                } else {
                    content.append(c);
                }
            }

            if (content.length() > 0) {
                String extracted = content.toString();
                // 检测提取出来的 content 是否也是 JSON 字符串（被 JSON 转义了）
                // 如果是，需要再做一次反转义
                if (extracted.contains("\\\"")) {
                    extracted = extracted.replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\\\", "\\");
                }
                return extracted;
            }
        } catch (Exception e) {
            // 解析失败就返回原文，不影响写入
        }
        return text;
    }

    /**
     * 在 JSON 字符串中查找指定 key 的位置（key 后的冒号位置）。
     */
    private static int findJsonKey(String json, String key) {
        // 匹配 "key":
        String pattern = "\"" + key + "\"\\s*:";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? matcher.end() : -1;
    }
}
