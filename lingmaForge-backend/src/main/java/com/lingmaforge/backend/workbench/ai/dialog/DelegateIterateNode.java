package com.lingmaforge.backend.workbench.ai.dialog;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.common.model.BuildResult;
import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.common.model.ProjectContext;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;
import com.lingmaforge.backend.workbench.service.ProjectService;
import com.lingmaforge.backend.workbench.service.SandboxService;

/**
 * 代码修改桥接节点。
 *
 * <p>Phase 3 改造为真实桥接：调用 {@link IterationAgent#modify(String)} 在已有项目上做增量补丁修改，
 * 随后调 {@link SandboxService#npmBuild(Long)} 做单次构建验证，结果写入 {@link DialogState#DELEGATE_RESULT}
 * 摘要并经 SSE 推前端。与 {@code GenerationService.runIteration} 几乎同构。</p>
 *
 * <p><b>编排顺序</b>：入口校验 → 设 {@link GenerationContext} → {@code emitNodeStart} →
 * {@code IterationAgent.modify}（工具自发 SSE）→ {@code emitNodeEnd} →
 * {@code SandboxService.npmBuild} 单次构建 → 写摘要 + 推 complete/error →
 * {@code finally} 清理 {@link GenerationContext}。</p>
 *
 * <p><b>emitter 获取</b>：本节点由 {@code DialogRouter} 以 {@code node_async} 派发到 fork-join 线程，
 * {@link GenerationContext} 的 ThreadLocal 不会跨线程传播。故通过
 * {@link GenerationStreamRegistry#get(String)} 按 {@code dialogId} 取 emitter；
 * 而 {@link GenerationContext} 在本节点所在线程内 {@code set} 后，{@code IterationAgent} 的工具
 * 在同一线程被 langchain4j 同步调用，能正确取到。</p>
 *
 * <p><b>停止控制</b>：仅在入口检查 {@link GenerationStreamRegistry#isStopRequested(String)}；
 * {@code modify} 开始后不可中断（langchain4j 的 AiServices 工具循环无中断点），等其跑完。</p>
 *
 * <p><b>构建失败不回退</b>：单次构建验证失败只写失败摘要 + 推 error 事件，不重新调 modify。</p>
 */
@Component
public class DelegateIterateNode {

    private static final Logger log = LoggerFactory.getLogger(DelegateIterateNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "delegate_iterate";

    /** 摘要中构建错误截断长度。 */
    private static final int SUMMARY_ERROR_LIMIT = 500;
    /** SSE error 事件中构建错误截断长度。 */
    private static final int SSE_ERROR_LIMIT = 200;

    private final IterationAgent iterationAgent;
    private final GenerationStreamRegistry streamRegistry;
    private final SandboxService sandboxService;
    private final ProjectService projectService;

    /**
     * 构造代码修改桥接节点。
     *
     * @param agentFactory   Agent 工厂，用于创建 {@link IterationAgent}
     * @param streamRegistry 流注册表，按 dialogId 取 emitter 与停止标志
     * @param sandboxService 沙箱服务，单次构建验证
     * @param projectService 项目服务，读取项目上下文拼 prompt
     */
    public DelegateIterateNode(AgentFactory agentFactory,
            GenerationStreamRegistry streamRegistry,
            SandboxService sandboxService,
            ProjectService projectService) {
        this.iterationAgent = agentFactory.createIterationAgent();
        this.streamRegistry = streamRegistry;
        this.sandboxService = sandboxService;
        this.projectService = projectService;
    }

    /**
     * 执行代码修改桥接。
     *
     * @param state 对话状态，须含 {@link DialogState#DIALOG_ID} 与 {@link DialogState#PROJECT_ID}
     * @return 状态更新，携带修改摘要（{@link DialogState#DELEGATE_RESULT}）
     */
    public Map<String, Object> execute(DialogState state) {
        String dialogId = state.dialogId().orElse("");
        String projectIdStr = state.projectId().orElse(null);
        String userMessage = state.userMessage().orElse("");

        // ---- 入口校验 ----
        if (projectIdStr == null || projectIdStr.isBlank()) {
            log.warn("[delegate_iterate] 未关联项目: dialogId={}", dialogId);
            return Map.of(DialogState.DELEGATE_RESULT, "[代码修改失败：未关联项目]");
        }
        GenerationStreamEmitter emitter = streamRegistry.get(dialogId);
        if (emitter == null) {
            log.warn("[delegate_iterate] SSE 连接未建立: dialogId={}", dialogId);
            return Map.of(DialogState.DELEGATE_RESULT, "[代码修改失败：SSE 连接未建立]");
        }
        if (streamRegistry.isStopRequested(dialogId)) {
            log.info("[delegate_iterate] 用户已取消: dialogId={}", dialogId);
            return Map.of(DialogState.DELEGATE_RESULT, "[代码修改已取消]");
        }

        Long projectId = Long.valueOf(projectIdStr);
        GenerationContext.set(projectId, dialogId, emitter);
        try {
            emitter.emitNodeStart(NODE_NAME, "正在理解修改意图并定位代码...");
            String contextSummary = buildIterationContext(projectId);
            String fullPrompt = "用户修改指令: " + userMessage + "\n\n项目上下文:\n" + contextSummary;

            String agentReply = iterationAgent.modify(fullPrompt);
            emitter.emitNodeEnd(NODE_NAME);

            // ---- 单次构建验证 ----
            BuildResult buildResult = sandboxService.npmBuild(projectId);
            String summary;
            if (buildResult.status() == BuildStatus.SUCCESS) {
                long buildSeconds = buildResult.durationMillis() / 1000;
                summary = "修改已完成，构建通过（耗时 " + buildSeconds + "s）。\n\n" + agentReply;
                emitter.complete("", 0, (int) buildSeconds);
            } else {
                String truncatedError = truncate(buildResult.error(), SUMMARY_ERROR_LIMIT);
                summary = "修改已应用，但构建失败：\n" + truncatedError + "\n\n" + agentReply;
                emitter.error("构建失败: " + truncate(buildResult.error(), SSE_ERROR_LIMIT));
            }
            log.info("[delegate_iterate] 完成: dialogId={}, buildStatus={}", dialogId, buildResult.status());
            return Map.of(DialogState.DELEGATE_RESULT, summary);
        } catch (Exception e) {
            log.error("[delegate_iterate] 失败: dialogId={}", dialogId, e);
            emitter.error("代码修改失败: " + e.getMessage());
            return Map.of(DialogState.DELEGATE_RESULT, "[代码修改失败]");
        } finally {
            GenerationContext.clear();
        }
    }

    /**
     * 拼接项目上下文摘要（框架 + 文件列表），借鉴
     * {@code GenerationService.buildIterationContext}。
     */
    private String buildIterationContext(Long projectId) {
        ProjectContext ctx = projectService.getProjectContext(projectId);
        return "框架: " + ctx.framework() + "\n文件列表:\n" + String.join("\n", ctx.filePaths());
    }

    /** 截断字符串到指定长度，超长追加省略号。 */
    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
}
