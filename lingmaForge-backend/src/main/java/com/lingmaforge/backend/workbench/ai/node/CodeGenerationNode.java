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

    /**
     * {@link #unwrapJsonEnvelope(String)} 专用的独立 {@link ObjectMapper}。
     *
     * <p>这是 static 字段：cleanup 系列方法不依赖 Spring 注入的 {@link #objectMapper} 实例，
     * 同时避免在每次调用时重复构造解析器。Jackson 的 ObjectMapper 是线程安全的，
     * 可安全地被多个并行生成线程共享。</p>
     */
    private static final ObjectMapper CLEANUP_MAPPER = new ObjectMapper();

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
            //    （shouldRegenerate 内部会调用 projectFileService.listFilePaths 做 DB 查询，
            //     这里只过滤一次，避免下游 stream 重复触发 DB 调用）
            List<FilePlan> filesToGenerate = buildError == null
                    ? filePlans
                    : filePlans.stream()
                        .filter(fp -> shouldRegenerate(fp, buildError, projectId))
                        .toList();

            List<CompletableFuture<Void>> futures = filesToGenerate.stream()
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
     *
     * <p>规则 2 中「配置文件/入口文件」的判定优先使用 {@link FilePlan#fileType()}
     * （由执行规划节点标注为 {@code config} 或 {@code entry}），兜底用路径后缀匹配
     * {@link #isEntryOrConfigPath(String)}，覆盖 fileType 缺失或标注不规范的情况。</p>
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
            // 只重新生成配置文件与入口文件
            return isEntryOrConfigFile(filePlan);
        }
        return false;
    }

    /**
     * 判断文件计划是否为入口文件或配置文件（模块级错误时需要重新生成）。
     *
     * <p>优先使用 {@link FilePlan#fileType()}（执行规划节点的语义标注），
     * 当 fileType 为 {@code config} 或 {@code entry} 时直接返回 true；
     * 否则用路径后缀/子串匹配兜底，覆盖 fileType 缺失或标注不规范的情况。</p>
     */
    private static boolean isEntryOrConfigFile(FilePlan filePlan) {
        String path = filePlan.path();
        if (path == null) {
            return false;
        }
        String fileType = filePlan.fileType();
        if ("config".equalsIgnoreCase(fileType) || "entry".equalsIgnoreCase(fileType)) {
            return true;
        }
        return isEntryOrConfigPath(path);
    }

    /**
     * 路径后缀/子串匹配判定入口/配置文件（兜底规则）。
     *
     * <p>覆盖主流前端工程化框架的常见配置文件与入口文件：
     * 包管理（package.json）、构建工具（vite.config / next.config / webpack.config / rollup.config /
     *  postcss.config / tailwind.config / .babelrc）、TS（tsconfig）、Lint（.eslintrc / .prettierrc）、
     * HTML 入口（index.html）、框架入口（App.tsx / App.jsx / App.vue / main.ts / main.tsx / main.js /
     *  index.ts / index.js）。</p>
     */
    private static boolean isEntryOrConfigPath(String path) {
        return path.endsWith("package.json")
                || path.contains("vite.config")
                || path.contains("next.config")
                || path.contains("webpack.config")
                || path.contains("rollup.config")
                || path.contains("postcss.config")
                || path.contains("tailwind.config")
                || path.contains("tsconfig")
                || path.endsWith(".eslintrc") || path.endsWith(".eslintrc.js") || path.endsWith(".eslintrc.json")
                || path.endsWith(".babelrc")
                || path.endsWith(".prettierrc")
                || path.endsWith("App.tsx") || path.endsWith("App.jsx") || path.endsWith("App.vue")
                || path.endsWith("main.ts") || path.endsWith("main.tsx") || path.endsWith("main.js")
                || path.endsWith("index.ts") || path.endsWith("index.js") || path.endsWith("index.html");
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
     *   <li>JSON 信封：{"path":"...","content":"真正的代码","status":"new"} 或 {"content":"真正的代码"}</li>
     *   <li>客套话："好的，以下是xxx的代码..."</li>
     * </ol>
     *
     * <p>处理顺序非常重要，必须先剥离客套话与 Markdown 围栏，再做 JSON 信封解包：
     * 模型常以「客套话 + 围栏 + JSON 信封」的复合形式输出，若先解包 JSON，
     * 围栏与客套话会让文本不以 {@code {} 开头而跳过解包；若先解包再剥离围栏，
     * 解包后的纯代码中可能残留围栏标记。因此采用「客套话 → 开头围栏 → 结尾围栏 → JSON 解包」
     * 的顺序，并在解包后再次检查是否仍有围栏残留（解包出的内容本身被围栏包裹的情况）。</p>
     *
     * @param raw 大模型原始输出
     * @return 清理后的纯代码
     */
    static String cleanupCodeOutput(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String cleaned = raw.stripLeading();

        // 1. 去掉开头的客套话行（"好的，以下是..." / "让我..."）。
        //    先剥离客套话，避免「客套话 + 围栏」组合时开头围栏无法被识别。
        cleaned = stripChattyPrefix(cleaned);

        // 2. 剥离开头与结尾的 Markdown 围栏（```tsx / ```typescript / ``` 等）。
        //    剥离围栏后，被围栏包裹的 JSON 信封才会暴露出来以便后续解包。
        cleaned = stripMarkdownFences(cleaned);

        // 3. 检测并解包 JSON 信封（模型模拟 writeFile 工具调用格式时出现）。
        cleaned = unwrapJsonEnvelope(cleaned);

        // 4. 解包后的内容可能再次被围栏包裹（content 字段值本身含 ```lang ... ```），
        //    再次剥离一次围栏以确保得到纯代码。
        cleaned = stripMarkdownFences(cleaned);

        // 5. 若解包/剥离后开头仍残留客套话，再清理一次。
        cleaned = stripChattyPrefix(cleaned);

        return cleaned.stripTrailing();
    }

    /**
     * 剥离开头的客套话行（"好的，以下是..." / "让我..." 等常见开头）。
     *
     * <p>持续跳过明确识别为客套话的行，直到遇到非客套话行（代码 / 注释 / 其他）。
     * 最多检查前 10 行，避免对超大输出做全文扫描。</p>
     *
     * <p>识别规则（保守，宁可漏剥也不误剥代码）：</p>
     * <ul>
     *   <li>行以「好的」「让我」「为您」「请」「我们」「这是」「以下是」等明确的客套话短语
     *       <strong>开头</strong>，且长度 &lt; 60（避免误判含这些字的正常长行）；
     *       或者整行以中文标点（：。）结尾的短句（&lt; 40 字）。</li>
     *   <li>非客套话行（代码关键字 / 注释 / 其他内容）一旦遇到即停止跳过。</li>
     * </ul>
     */
    private static String stripChattyPrefix(String text) {
        String[] lines = text.split("\n", -1);
        int codeStart = 0;
        for (int i = 0; i < Math.min(lines.length, 10); i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) continue;
            // 遇到代码关键字 / 代码符号开头 / 注释开头，立即停止跳过
            if (trimmed.matches("^(import|export|const|let|var|function|interface|type|class|enum|declare|namespace|public|private|protected|static|abstract|@|\\{|\\[|<).*")
                    || trimmed.startsWith("#")
                    || trimmed.startsWith("/*") || trimmed.startsWith("//") || trimmed.startsWith("<!--")) {
                codeStart = i;
                break;
            }
            // 客套话判定：行以常见客套话短语开头且较短
            if (isChattyLine(trimmed)) {
                codeStart = i + 1;
                continue;
            }
            // 非客套话、非代码：保留该行（可能是注释说明或正文），停止跳过
            codeStart = i;
            break;
        }
        if (codeStart > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = codeStart; i < lines.length; i++) {
                if (i > codeStart) sb.append('\n');
                sb.append(lines[i]);
            }
            return sb.toString();
        }
        return text;
    }

    /**
     * 判断一行是否为客套话/引导语。
     *
     * <p>要求行以明确的客套话短语开头（而非仅包含其中某个字），且长度较短。
     * 这样可以避免误判含中文字符的正常代码行（如 {@code const msg = "好的";}）。</p>
     */
    private static boolean isChattyLine(String trimmed) {
        // 以常见客套话短语开头
        if (trimmed.length() < 60
                && (trimmed.startsWith("好的") || trimmed.startsWith("让我") || trimmed.startsWith("为您")
                        || trimmed.startsWith("请") || trimmed.startsWith("我们") || trimmed.startsWith("这是")
                        || trimmed.startsWith("以下是") || trimmed.startsWith("下面是") || trimmed.startsWith("接下来"))) {
            return true;
        }
        // 短中文句且以中文冒号/句号结尾（典型的引导语结尾）
        if (trimmed.length() < 40 && trimmed.length() >= 2
                && (trimmed.endsWith("：") || trimmed.endsWith("。"))
                && trimmed.codePoints().limit(1).findFirst().orElse(0) > 0x2E7F) {
            // 首字符是 CJK 字符（U+2E80 起为 CJK Radicals），配合短长度+标点结尾判定
            return true;
        }
        return false;
    }

    /**
     * 剥离开头与结尾的 Markdown 围栏标记（```）。
     *
     * <p>开头围栏形如 {@code ```tsx} 或 {@code ```typescript}，去掉首行；
     * 结尾围栏为最后的 {@code ```}，去掉其后内容。两者独立处理，互不影响。</p>
     */
    private static String stripMarkdownFences(String text) {
        String cleaned = text;
        // 开头围栏
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        // 结尾围栏
        int lastFence = cleaned.lastIndexOf("```");
        if (lastFence != -1) {
            cleaned = cleaned.substring(0, lastFence).stripTrailing();
        }
        return cleaned;
    }

    /**
     * 检测并解包 JSON 信封格式。
     *
     * <p>某些大模型会输出类似 writeFile 工具调用的 JSON 包裹：
     * <pre>{"path": "tsconfig.node.json", "content": "{\n  \"compilerOptions\": {...}}", "status": "new"}</pre>
     * 这种格式下，实际文件内容在 "content" 字段的值中（可能还被 JSON 转义了）。
     * 使用 Jackson 正确解析 JSON 并提取 content 字段，能够完整处理嵌套转义
     * （如 content 值本身是 JSON 字符串，其中的 \n / \" / \t 会被 Jackson 自动反转义）。</p>
     *
     * <p>仅当文本以 {@code {} 开头、能被解析为 JSON 对象、且包含 {@code content} 字段时才解包；
     * 否则原样返回，避免误伤本身就是 JSON 的配置文件（如无 content 字段的 package.json）。</p>
     */
    private static String unwrapJsonEnvelope(String text) {
        String trimmed = text.stripLeading();
        if (!trimmed.startsWith("{")) return text;

        try {
            // 只处理包含 "content" 字段的情况（writeFile 信封的签名）
            if (!trimmed.contains("\"content\"")) return text;

            com.fasterxml.jackson.databind.JsonNode root = CLEANUP_MAPPER.readTree(trimmed);
            if (!root.isObject() || !root.has("content")) return text;

            com.fasterxml.jackson.databind.JsonNode contentNode = root.get("content");
            if (contentNode == null || contentNode.isNull()) return text;

            // content 字段可能是字符串（常见）或对象/数组（罕见），统一转成字符串
            String extracted;
            if (contentNode.isTextual()) {
                extracted = contentNode.asText();
            } else {
                extracted = CLEANUP_MAPPER.writeValueAsString(contentNode);
            }

            return extracted == null || extracted.isEmpty() ? text : extracted;
        } catch (Exception e) {
            // 解析失败就返回原文，不影响写入
            return text;
        }
    }
}
