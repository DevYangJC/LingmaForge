package com.lingmaforge.backend.workbench.ai.node;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.CodeGenAgent;
import com.lingmaforge.backend.workbench.ai.stream.StreamingBridge;
import com.lingmaforge.backend.workbench.ai.stream.StreamingContext;
import com.lingmaforge.backend.workbench.ai.memory.ContextCompactionService;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;

/**
 * 代码生成节点——已收敛为 agentic 工具循环（对齐 zero-code 模式）。
 *
 * <p>旧版用 {@code CompletableFuture} 并行生成文件，每个文件"盲写"看不到其他文件的内容。
 * 新版用单一 {@code TokenStream} agent 循环，模型自主：
 * <ul>
 *   <li>{@code readProjectContext} → 了解模板基座（package.json/vite.config/tsconfig）</li>
 *   <li>{@code updatePlan} → 声明文件生成计划（DAG）</li>
 *   <li>{@code writeFile} → 按顺序写文件（写完一个再写下一个，能看到前面文件的内容）</li>
 *   <li>{@code readFileContext} → 在写后续文件前确认已有文件内容</li>
 *   <li>{@code exit} → 全部完成后退出</li>
 * </ul>
 */
@Component
public class CodeGenerationNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationNode.class);

    public static final String NODE_NAME = "code_generation";

    private final CodeGenAgent agent;
    private final StreamingBridge streamingBridge;
    private final ContextCompactionService compactionService;
    private final java.util.function.Function<String, MessageWindowChatMemory> chatMemoryProvider;

    public CodeGenerationNode(AgentFactory agentFactory,
            GenerationStreamRegistry streamRegistry,
            StreamingBridge streamingBridge,
            ContextCompactionService compactionService,
            java.util.function.Function<String, MessageWindowChatMemory> chatMemoryProvider) {
        super(streamRegistry);
        this.agent = agentFactory.createCodeGenAgent();
        this.streamingBridge = streamingBridge;
        this.compactionService = compactionService;
        this.chatMemoryProvider = chatMemoryProvider;
    }

    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在生成项目代码...");
        Long projectId = projectId(state);
        String taskId = state.taskId().orElse("");
        String buildError = state.buildError().orElse(null);

        try {
            String prompt = buildCodeGenPrompt(state.prompt().orElse(""), buildError, projectId);

            String memoryKey = projectId + "_vue-project";
            compactionService.autoCompactIfNeeded(
                    chatMemoryProvider.apply(memoryKey), memoryKey);

            log.info("[{}] agentic 代码生成开始：projectId={}", taskId, projectId);

            StreamingContext ctx = StreamingContext.builder()
                    .emitter(emitter)
                    .nodeName(NODE_NAME)
                    .taskId(taskId)
                    .stopRegistry(getStreamRegistry())
                    .onToken(t -> emitter.emitNode(NODE_NAME, t, "TEXT"))
                    .onComplete(() -> emitter.emitNode(NODE_NAME, "代码生成完成", "TEXT"))
                    .build();

            streamingBridge.bridge(agent.generate(projectId, prompt), ctx);

            return Map.of();
        } catch (Exception e) {
            log.error("[{}] agentic 代码生成失败", taskId, e);
            emitter.emitNode(NODE_NAME, "代码生成失败: " + e.getMessage(), "TEXT");
            throw new RuntimeException("代码生成失败", e);
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }

    private String buildCodeGenPrompt(String userPrompt, String buildError, Long projectId) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个资深 Vue 3 + TypeScript 前端工程师。\n\n");
        sb.append("## 用户需求\n").append(userPrompt).append("\n\n");

        if (buildError != null && !buildError.isBlank()) {
            sb.append("## 上次构建失败（必须修复！）\n").append(buildError).append("\n\n");
        }

        sb.append("""
                ## 工作流程（必须遵守）
                1. 先调用 readProjectContext 了解项目现有文件结构和依赖
                2. 调用 updatePlan 创建文件生成计划（DAG），先写配置文件再写组件再写页面
                3. 按计划一项一项执行：对每个文件调用 writeFile 写入代码
                4. 写后续文件前，用 readFileContext 确认前面已写文件的内容（确保 import 路径正确）
                5. 每完成一项调用 updatePlan 更新进度
                6. 全部完成后调用 exit

                ## 规则
                - 不要生成 package.json / vite.config.ts / tsconfig*.json —— 这些模板文件已就绪
                - 每个文件写完后立即 updatePlan，不要攒到最后
                - 用 writeFile 写入文件内容时，确保 TypeScript 类型完整，import 路径指向已存在的文件
                - 禁止生成以下内容：安装步骤说明、技术栈说明、使用指导、客套话
                """);

        return sb.toString();
    }
}