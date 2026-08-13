# Phase 3 — 代码修改子图（接 IterationAgent）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `DelegateIterateNode` 从桩节点改造为真实桥接——调用 `IterationAgent.modify` 做增量补丁 + 单次 `SandboxService.npmBuild` 构建验证，结果经 SSE 推前端并落库。

**Architecture:** 方案 A（内联编排）。`DelegateIterateNode.execute()` 顺序执行：入口校验 → 设 `GenerationContext` → `IterationAgent.modify`（工具自发 SSE）→ `SandboxService.npmBuild` 单次构建 → 写 `DELEGATE_RESULT` 摘要 + 推 complete/error。不引入迭代子 StateGraph，不动 `DialogRouter`/`DialogState`，不动 Phase 0 固化的 `BuildVerificationNode`。

**Tech Stack:** Java 21 / Spring Boot 3.5 / langgraph4j 1.8.19 / langchain4j 1.16.2 / Mockito / AssertJ / MyBatis-Plus

**关联设计文档:** `项目文档/方案文档/Phase3-代码修改子图设计.md`

**JDK 路径:** `D:\Develop\DevelopTool\StudyEnvironment\PhpWebStudy-Data\app\openjdk-21.0.9`
**Maven Wrapper:** `lingmaForge-backend/mvnw`
**测试排除:** 预存的 `ModelConnectivityTest` 需真实 API Key，始终用 `-Dtest='!ModelConnectivityTest'` 排除。

---

## 文件结构

**新建（3 个）**：
1. `src/test/.../ai/dialog/DelegateIterateNodeTest.java` — DelegateIterateNode 单元测试（7 例）
2. `src/test/.../testconfig/StubIterateAgentConfig.java` — 集成测试用 mock AgentFactory（迭代场景）
3. `docs/superpowers/plans/2026-08-13-phase3-delegate-iterate.md` — 本计划

**修改（4 个）**：
1. `workbench/ai/dialog/DelegateIterateNode.java` — 桩 → 真实桥接
2. `workbench/service/ChatService.java` — runDialog 兜底 complete 逻辑调整
3. `src/test/.../ai/dialog/DialogRouterTest.java` — 适配 DelegateIterateNode 新构造（setUp + routerWithAnalyzer）
4. `src/test/.../ai/dialog/ChatFlowIntegrationTest.java` — 新增迭代场景

**不动**：`DialogRouter.java` / `DialogState.java` / `AgentFactory.java` / `IterationAgent.java` / `IterationTools.java` / `FileTools.java` / `ProjectContextTools.java` / `SandboxService.java` / `BuildVerificationNode.java` / `ChatController.java` / 前端

---

## Task 1: DelegateIterateNode 真实桥接实现

**Files:**
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/dialog/DelegateIterateNode.java`

**前置说明：** 本任务先把节点改成真实实现，测试在 Task 2 补。因为节点构造签名变了（新增 4 个依赖），`DialogRouterTest` 会编译失败——Task 3 会修。Task 1-3 必须连续做完才能恢复绿。

- [ ] **Step 1: 用真实实现替换桩节点**

把 `DelegateIterateNode.java` 全文替换为：

```java
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
```

- [ ] **Step 2: 提交（允许编译暂不过）**

```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge
git add lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/dialog/DelegateIterateNode.java
git commit -m "feat(dialog): DelegateIterateNode 桥接 IterationAgent + 单次构建验证

桩节点改造为真实实现：入口校验 → GenerationContext → modify → npmBuild → 写摘要。
构造新增 4 依赖，测试适配在后续步骤完成。"
```

---

## Task 2: DelegateIterateNode 单元测试

**Files:**
- Create: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/dialog/DelegateIterateNodeTest.java`

**设计：** `@ExtendWith(MockitoExtension.class)`，mock `AgentFactory`/`GenerationStreamRegistry`/`SandboxService`/`ProjectService`。`AgentFactory.createIterationAgent()` 返回 mock `IterationAgent`，其 `modify(anyString())` 默认返回 `"修改完成"`。7 个测试覆盖入口校验、成功、构建失败、modify 异常、GenerationContext 清理。

- [ ] **Step 1: 写测试文件**

创建 `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/dialog/DelegateIterateNodeTest.java`：

```java
package com.lingmaforge.backend.ai.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lingmaforge.backend.common.model.BuildResult;
import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.common.model.ProjectContext;
import com.lingmaforge.backend.workbench.ai.dialog.DelegateIterateNode;
import com.lingmaforge.backend.workbench.ai.dialog.DialogState;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;
import com.lingmaforge.backend.workbench.service.ProjectService;
import com.lingmaforge.backend.workbench.service.SandboxService;

/**
 * DelegateIterateNode 单元测试。
 *
 * <p>覆盖入口校验（projectId/emitter/停止）、modify 成功 + 构建成功/失败、
 * modify 异常、GenerationContext 清理。</p>
 */
@DisplayName("DelegateIterateNode 单元测试")
@ExtendWith(MockitoExtension.class)
class DelegateIterateNodeTest {

    @Mock private AgentFactory agentFactory;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private SandboxService sandboxService;
    @Mock private ProjectService projectService;
    @Mock private GenerationStreamEmitter emitter;
    @Mock private IterationAgent iterationAgent;

    private DelegateIterateNode node;

    @BeforeEach
    void setUp() {
        lenient().when(agentFactory.createIterationAgent()).thenReturn(iterationAgent);
        lenient().when(streamRegistry.get(anyString())).thenReturn(emitter);
        lenient().when(streamRegistry.isStopRequested(anyString())).thenReturn(false);
        lenient().when(iterationAgent.modify(anyString())).thenReturn("修改完成");
        lenient().when(projectService.getProjectContext(anyLong()))
                .thenReturn(new ProjectContext("vue", List.of("src/App.vue"), List.of()));
        node = new DelegateIterateNode(agentFactory, streamRegistry, sandboxService, projectService);
    }

    private DialogState stateWith(String dialogId, String projectId, String userMessage) {
        Map<String, Object> data = new java.util.HashMap<>();
        if (dialogId != null) data.put(DialogState.DIALOG_ID, dialogId);
        if (projectId != null) data.put(DialogState.PROJECT_ID, projectId);
        if (userMessage != null) data.put(DialogState.USER_MESSAGE, userMessage);
        return new DialogState(data);
    }

    @Test
    @DisplayName("projectId 缺失 → 返回失败摘要，不调 modify")
    void shouldFailWhenProjectIdMissing() {
        DialogState state = stateWith("d1", null, "改成蓝色");
        Map<String, Object> result = node.execute(state);
        assertThat(result.get(DialogState.DELEGATE_RESULT))
                .asString().contains("未关联项目");
        org.mockito.Mockito.verifyNoInteractions(iterationAgent, sandboxService);
    }

    @Test
    @DisplayName("emitter 缺失 → 返回失败摘要，不调 modify")
    void shouldFailWhenEmitterMissing() {
        when(streamRegistry.get("d1")).thenReturn(null);
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        assertThat(result.get(DialogState.DELEGATE_RESULT))
                .asString().contains("SSE 连接未建立");
        org.mockito.Mockito.verifyNoInteractions(iterationAgent, sandboxService);
    }

    @Test
    @DisplayName("已请求停止 → 返回已取消摘要，不调 modify")
    void shouldReturnCancelledWhenStopRequested() {
        when(streamRegistry.isStopRequested("d1")).thenReturn(true);
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        assertThat(result.get(DialogState.DELEGATE_RESULT))
                .asString().contains("已取消");
        org.mockito.Mockito.verifyNoInteractions(iterationAgent, sandboxService);
    }

    @Test
    @DisplayName("modify 成功 + 构建成功 → 摘要含修改完成与构建通过，推 complete")
    void shouldSucceedWhenBuildPasses() {
        when(sandboxService.npmBuild(100L))
                .thenReturn(new BuildResult(BuildStatus.SUCCESS, "ok", null, 3000L));
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        String summary = (String) result.get(DialogState.DELEGATE_RESULT);
        assertThat(summary).contains("修改完成").contains("构建通过");
        verify(emitter).complete("", 0, 3);
    }

    @Test
    @DisplayName("modify 成功 + 构建失败 → 摘要含构建失败，推 error")
    void shouldReportErrorWhenBuildFails() {
        when(sandboxService.npmBuild(100L))
                .thenReturn(new BuildResult(BuildStatus.FAILED, "out", "SyntaxError", 1000L));
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        String summary = (String) result.get(DialogState.DELEGATE_RESULT);
        assertThat(summary).contains("构建失败").contains("修改完成");
        verify(emitter).error(org.mockito.ArgumentMatchers.contains("构建失败"));
    }

    @Test
    @DisplayName("modify 抛异常 → 返回失败摘要，推 error")
    void shouldFailWhenModifyThrows() {
        when(iterationAgent.modify(anyString()))
                .thenThrow(new IllegalStateException("模型不可用"));
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        assertThat(result.get(DialogState.DELEGATE_RESULT))
                .asString().contains("代码修改失败");
        verify(emitter).error(org.mockito.ArgumentMatchers.contains("代码修改失败"));
    }

    @Test
    @DisplayName("modify 异常后 GenerationContext 已清理")
    void shouldClearContextAfterModifyFailure() {
        when(iterationAgent.modify(anyString()))
                .thenThrow(new IllegalStateException("模型不可用"));
        DialogState state = stateWith("d1", "100", "改成蓝色");
        node.execute(state);
        // GenerationContext.get() 在 clear 后应抛 IllegalStateException
        assertThatThrownBy(GenerationContext::get)
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: 运行测试（此时会编译失败，因为 DialogRouterTest 还没适配新构造）**

Run:
```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge/lingmaForge-backend
export JAVA_HOME="/d/Develop/DevelopTool/StudyEnvironment/PhpWebStudy-Data/app/openjdk-21.0.9"
./mvnw -q -Dtest='DelegateIterateNodeTest' test
```
Expected: 编译失败（`DialogRouterTest` 引用 `new DelegateIterateNode()` 无参构造）。这是预期的——Task 3 修好后重跑。

- [ ] **Step 3: 暂不提交，继续 Task 3**

---

## Task 3: DialogRouterTest 适配新构造

**Files:**
- Modify: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/dialog/DialogRouterTest.java`

**改动点：** `DelegateIterateNode` 构造从无参变为 4 参。`DialogRouterTest` 有两处 `new DelegateIterateNode()`：
1. `setUp()` 第 70 行
2. `EndToEndExecution.routerWithAnalyzer()` 第 285 行

两处都需改为 `new DelegateIterateNode(agentFactory, streamRegistry, sandboxService, projectService)`，并新增 `@Mock SandboxService` / `@Mock ProjectService` 字段 + `lenient()` 打桩 `createIterationAgent()`。

- [ ] **Step 1: 新增 mock 字段**

在 `DialogRouterTest` 类的 `@Mock private GenerationStreamRegistry streamRegistry;` 之后，新增：

```java
    @Mock private com.lingmaforge.backend.workbench.service.SandboxService sandboxService;
    @Mock private com.lingmaforge.backend.workbench.service.ProjectService projectService;
```

- [ ] **Step 2: setUp() 适配**

在 `setUp()` 的 `lenient().when(agentFactory.createChatReplyAgent()).thenReturn(stubChatAgent);` 之后，新增对 `createIterationAgent` 的打桩：

```java
        // DelegateIterateNode 构造会调用 createIterationAgent()，打桩返回 mock agent
        com.lingmaforge.backend.workbench.ai.service.IterationAgent stubIterateAgent =
                mock(com.lingmaforge.backend.workbench.ai.service.IterationAgent.class);
        lenient().when(agentFactory.createIterationAgent()).thenReturn(stubIterateAgent);
```

把 `setUp()` 中的：
```java
        DelegateIterateNode delegateIterateNode = new DelegateIterateNode();
```
改为：
```java
        DelegateIterateNode delegateIterateNode = new DelegateIterateNode(
                agentFactory, streamRegistry, sandboxService, projectService);
```

- [ ] **Step 3: routerWithAnalyzer() 适配**

在 `routerWithAnalyzer()` 方法内，把：
```java
            DialogRouter r = new DialogRouter(
                    intentDetectionNode, new DelegateCodegenNode(),
                    new DelegateIterateNode(),
                    new ChatReplyNode(agentFactory, streamRegistry));
```
改为：
```java
            DialogRouter r = new DialogRouter(
                    intentDetectionNode, new DelegateCodegenNode(),
                    new DelegateIterateNode(agentFactory, streamRegistry, sandboxService, projectService),
                    new ChatReplyNode(agentFactory, streamRegistry));
```

- [ ] **Step 4: 运行 DialogRouterTest 验证绿**

Run:
```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge/lingmaForge-backend
export JAVA_HOME="/d/Develop/DevelopTool/StudyEnvironment/PhpWebStudy-Data/app/openjdk-21.0.9"
./mvnw -q -Dtest='DialogRouterTest' test
```
Expected: BUILD SUCCESS，全部 12 例通过

- [ ] **Step 5: 运行 DelegateIterateNodeTest 验证绿**

Run:
```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge/lingmaForge-backend
export JAVA_HOME="/d/Develop/DevelopTool/StudyEnvironment/PhpWebStudy-Data/app/openjdk-21.0.9"
./mvnw -q -Dtest='DelegateIterateNodeTest' test
```
Expected: BUILD SUCCESS，全部 7 例通过

- [ ] **Step 6: 提交**

```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge
git add lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/dialog/DelegateIterateNodeTest.java \
        lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/dialog/DialogRouterTest.java
git commit -m "test(dialog): DelegateIterateNode 7 例单测 + DialogRouterTest 适配新构造"
```

---

## Task 4: ChatService.runDialog 兜底逻辑调整

**Files:**
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/service/ChatService.java`（runDialog 方法，约第 234-246 行）

**问题：** Phase 2 的兜底逻辑是"非 chat 意图推 complete 兜底"。Phase 3 的 DelegateIterateNode 会自己推 complete（构建成功）或 error（构建失败/异常）。ChatService 再推一次 complete 会产生重复事件。

**改法：** 把"非 chat 意图推 complete 兜底"改为"reply 为空才推 complete 兜底"。reply 非空说明 delegate 节点已自处理完成信号；reply 为空说明 delegate 节点异常未写 DELEGATE_RESULT，需兜底避免前端无限等待。

- [ ] **Step 1: 修改 runDialog 的兜底分支**

把 `ChatService.java` 的 `runDialog` 方法中这段：

```java
            if (last != null) {
                String reply = last.state().delegateResult().orElse("");
                DialogIntent intent = last.state().intent().orElse(DialogIntent.CHAT);
                // 落库 assistant 回复
                if (reply != null && !reply.isBlank()) {
                    saveChatMessage(projectId, dialogId, "assistant", reply);
                }
                // 非 chat 意图（桩节点）：推一个 complete 兜底，避免前端无限等待
                if (intent != DialogIntent.CHAT) {
                    emitter.complete("", 0, 0);
                }
                // chat 意图的 complete 事件已由 ChatReplyNode.emitChatComplete 推送，这里不重复
            }
```

改为：

```java
            if (last != null) {
                String reply = last.state().delegateResult().orElse("");
                // 落库 assistant 回复
                if (reply != null && !reply.isBlank()) {
                    saveChatMessage(projectId, dialogId, "assistant", reply);
                }
                // 只有 delegate 节点没自己推过 complete/error 时才兜底。
                // Phase 2 闲聊：ChatReplyNode 已推 emitChatComplete（message 事件带 complete 标志）；
                // Phase 3 迭代：DelegateIterateNode 已推 complete/error 事件。
                // 故这里只在 reply 为空（异常情况）时推 complete 兜底，避免前端无限等待。
                if (reply == null || reply.isBlank()) {
                    emitter.complete("", 0, 0);
                }
            }
```

同时移除不再使用的 `DialogIntent intent` 局部变量与 `import com.lingmaforge.backend.workbench.ai.dialog.DialogIntent;`（如果编译器报 unused）。检查文件顶部 import——若 `DialogIntent` 不再被本类其他方法使用，删除该 import 行。

- [ ] **Step 2: 编译验证**

Run:
```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge/lingmaForge-backend
export JAVA_HOME="/d/Develop/DevelopTool/StudyEnvironment/PhpWebStudy-Data/app/openjdk-21.0.9"
./mvnw -q compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge
git add lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/service/ChatService.java
git commit -m "refactor(chat): runDialog 兜底改为 reply 为空才推 complete，避免与 delegate 节点重复"
```

---

## Task 5: StubIterateAgentConfig 测试配置

**Files:**
- Create: `lingmaForge-backend/src/test/java/com/lingmaforge/testconfig/StubIterateAgentConfig.java`

**设计：** 仿 `StubChatAgentConfig`，用 `@TestConfiguration` + `@Primary` mock `AgentFactory`：`createIntentAnalyzer()` 返回固定 `MODIFY_CODE` 意图，`createIterationAgent()` 返回固定 "修改完成" 的 mock agent，`createChatReplyAgent()` 返回 mock（lenient，迭代路径不走）。放 `com.lingmaforge.testconfig` 包（ComponentScan 范围外），只 `@Import` 它的测试才加载。

- [ ] **Step 1: 写配置类**

创建 `lingmaForge-backend/src/test/java/com/lingmaforge/testconfig/StubIterateAgentConfig.java`：

```java
package com.lingmaforge.testconfig;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.lingmaforge.backend.testutil.StubTokenStream;
import com.lingmaforge.backend.workbench.ai.dialog.IntentResult;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.service.ChatReplyAgent;
import com.lingmaforge.backend.workbench.ai.service.IntentAnalyzer;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;

/**
 * 测试专用 AgentFactory 覆盖配置——使意图识别固定返回 MODIFY_CODE、
 * 迭代修改返回固定文本 "修改完成"。
 *
 * <p>放在 {@code com.lingmaforge.testconfig} 包（不在主应用
 * {@code @ComponentScan} 扫描范围内），只有显式 {@code @Import} 的测试才加载。
 * 仿 {@link StubChatAgentConfig} 模式。</p>
 */
@TestConfiguration
public class StubIterateAgentConfig {

    @Bean
    @Primary
    public AgentFactory agentFactory() {
        AgentFactory factory = mock(AgentFactory.class);

        // 意图识别：固定返回 MODIFY_CODE
        IntentAnalyzer analyzer = mock(IntentAnalyzer.class);
        lenient().when(analyzer.analyze(anyString()))
                .thenReturn(new IntentResult("modify_code", 0.95));
        when(factory.createIntentAnalyzer()).thenReturn(analyzer);

        // 迭代修改：返回固定文本
        IterationAgent iterateAgent = mock(IterationAgent.class);
        when(iterateAgent.modify(anyString())).thenReturn("修改完成");
        when(factory.createIterationAgent()).thenReturn(iterateAgent);

        // 闲聊回复：lenient（迭代路径不走，但 DelegateIterateNode 构造时不调 createChatReplyAgent，
        // ChatReplyNode 构造时才调——若图未路由到 chat_reply 则不会被调用，故 lenient 防止 UnnecessaryStubbing）
        ChatReplyAgent chatAgent = mock(ChatReplyAgent.class);
        lenient().when(chatAgent.reply(anyString()))
                .thenReturn(new StubTokenStream("你好"));
        lenient().when(factory.createChatReplyAgent()).thenReturn(chatAgent);

        return factory;
    }
}
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge/lingmaForge-backend
export JAVA_HOME="/d/Develop/DevelopTool/StudyEnvironment/PhpWebStudy-Data/app/openjdk-21.0.9"
./mvnw -q test-compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge
git add lingmaForge-backend/src/test/java/com/lingmaforge/testconfig/StubIterateAgentConfig.java
git commit -m "test(config): 新增 StubIterateAgentConfig，mock 意图为 MODIFY_CODE + 迭代返回固定文本"
```

---

## Task 6: ChatFlowIntegrationTest 新增迭代场景

**Files:**
- Modify: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/dialog/ChatFlowIntegrationTest.java`

**设计：** 新增两个 `@Nested` 场景。场景三用 `@Import(StubIterateAgentConfig.class)` + mock `SandboxService`（通过 `@MockBean`）让迭代走成功路径。场景四用真实 Spring 上下文（NoOpModel 降级），验证 modify 抛异常时图不崩溃、错误正确传播。

**关键点：** 场景三需要 mock `SandboxService.npmBuild` 返回 SUCCESS——用 `@MockBean SandboxService` 覆盖真实 Bean。但 `@MockBean` 在 `@Nested` 类上需配合 `@SpringBootTest`。`CapturingEmitter` 需扩展捕获 `complete`/`error` 事件（当前只捕获 chatToken/error/completeResponse）。

- [ ] **Step 1: 扩展 CapturingEmitter 捕获 complete 事件**

在 `ChatFlowIntegrationTest.java` 的 `CapturingEmitter` 内部类中，新增字段与方法。把现有的：

```java
    static class CapturingEmitter implements GenerationStreamEmitter {

        final List<String> chatTokens = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        volatile String completeResponse;

        @Override
        public void emitChatToken(String token) {
            chatTokens.add(token);
        }

        @Override
        public void emitChatComplete(String fullResponse) {
            this.completeResponse = fullResponse;
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }

        // 以下方法本测试不关注，空实现
        @Override public void emitNode(String n, String t, String tt) {}
        @Override public void emitFile(String p, String c, String s) {}
        @Override public void emitLog(String t) {}
        @Override public void complete(String u, Integer po, Integer bt) {}
        @Override public void emitModification(String n, String t, String tt,
                List<com.lingmaforge.backend.common.model.FileModification> mods) {}
        @Override public void emitNodeStart(String nodeName, String title) {}
        @Override public void emitNodeEnd(String nodeName) {}
        @Override public void emitThinking(String nodeName, String token) {}
        @Override public void emitFileToken(String path, String token) {}
        @Override public void emitFileComplete(String path) {}
    }
```

替换为：

```java
    static class CapturingEmitter implements GenerationStreamEmitter {

        final List<String> chatTokens = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<Integer> completeBuildTimes = new ArrayList<>();
        volatile String completeResponse;

        @Override
        public void emitChatToken(String token) {
            chatTokens.add(token);
        }

        @Override
        public void emitChatComplete(String fullResponse) {
            this.completeResponse = fullResponse;
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }

        @Override
        public void complete(String url, Integer port, Integer buildTime) {
            completeBuildTimes.add(buildTime);
        }

        // 以下方法本测试不关注，空实现
        @Override public void emitNode(String n, String t, String tt) {}
        @Override public void emitFile(String p, String c, String s) {}
        @Override public void emitLog(String t) {}
        @Override public void emitModification(String n, String t, String tt,
                List<com.lingmaforge.backend.common.model.FileModification> mods) {}
        @Override public void emitNodeStart(String nodeName, String title) {}
        @Override public void emitNodeEnd(String nodeName) {}
        @Override public void emitThinking(String nodeName, String token) {}
        @Override public void emitFileToken(String path, String token) {}
        @Override public void emitFileComplete(String path) {}
    }
```

- [ ] **Step 2: 新增场景三（迭代成功）**

在 `NoOpFallbackScenario` 内部类之后（`CapturingEmitter` 之前），新增：

```java
    /**
     * 场景三：迭代修改端到端（Mock 模型 + 构建成功）。
     *
     * <p>用 {@link StubIterateAgentConfig} 覆盖 {@link AgentFactory}：意图固定 MODIFY_CODE、
     * IterationAgent.modify 返回 "修改完成"。用 {@code @MockBean SandboxService} 覆盖真实
     * 沙箱服务，npmBuild 返回 SUCCESS。验证 delegateResult 含修改完成 + 构建通过、
     * emitter 收到 complete 事件。</p>
     */
    @Nested
    @DisplayName("场景三：迭代修改端到端（Mock 模型 + 构建成功）")
    @SpringBootTest
    @Import(StubIterateAgentConfig.class)
    class IterateSuccessScenario {

        @Autowired private ApplicationContext context;

        @org.springframework.boot.test.mock.mockito.MockBean
        SandboxService sandboxService;

        @Test
        @DisplayName("MODIFY_CODE 意图 → 图执行 → emitter 收到 complete 事件")
        void shouldIterateEndToEndWithBuildSuccess() {
            // 安排：mock SandboxService.npmBuild 返回 SUCCESS
            org.mockito.Mockito.when(sandboxService.npmBuild(100L))
                    .thenReturn(new com.lingmaforge.backend.common.model.BuildResult(
                            com.lingmaforge.backend.common.model.BuildStatus.SUCCESS,
                            "ok", null, 2000L));

            DialogRouter router = context.getBean(DialogRouter.class);
            GenerationStreamRegistry registry = context.getBean(GenerationStreamRegistry.class);
            CapturingEmitter emitter = new CapturingEmitter();
            String dialogId = "test-dialog-iterate";
            registry.register(dialogId, emitter);

            log.info("--- 场景三：迭代修改端到端 ---");
            Map<String, Object> inputs = Map.of(
                    DialogState.DIALOG_ID, dialogId,
                    DialogState.USER_MESSAGE, "把首页改成蓝色",
                    DialogState.PROJECT_ID, "100");

            var finalState = router.getCompiledGraph().stream(inputs)
                    .stream()
                    .reduce((first, second) -> second)
                    .orElseThrow()
                    .state();

            assertThat(finalState.intent()).hasValue(DialogIntent.MODIFY_CODE);
            assertThat(finalState.delegateResult())
                    .as("DELEGATE_RESULT 应含 IterationAgent 回复 + 构建通过")
                    .hasValueSatisfying(s -> {
                        assertThat(s).contains("修改完成");
                        assertThat(s).contains("构建通过");
                    });
            assertThat(emitter.completeBuildTimes)
                    .as("应收到一个 complete 事件")
                    .hasSize(1);
            assertThat(emitter.errors)
                    .as("不应有错误事件")
                    .isEmpty();

            log.info("[OK] 场景三：intent=MODIFY_CODE, delegateResult 含修改完成+构建通过, complete 事件已收到");
            registry.unregister(dialogId);
        }
    }
```

**注意：** 场景三用 `@MockBean SandboxService` 覆盖真实 Bean。但 `DelegateIterateNode` 构造时注入的是 Spring 容器里的 `SandboxService`——`@MockBean` 会替换容器中的 Bean，故 `DelegateIterateNode` 拿到的是 mock。需确认 `ProjectService` 也是真实 Bean（测试 profile 有 schema.sql 初始化数据或 `getProjectContext` 不抛异常）。若 `getProjectContext(100L)` 因项目不存在抛异常，需额外 `@MockBean ProjectService`。**先用 `@MockBean ProjectService` 保险**，在场景三类内再加：

```java
        @org.springframework.boot.test.mock.mockito.MockBean
        ProjectService projectService;
```

并在 `@Test` 方法开头的"安排"区，`npmBuild` mock 之前加：

```java
            org.mockito.Mockito.when(projectService.getProjectContext(100L))
                    .thenReturn(new com.lingmaforge.backend.common.model.ProjectContext(
                            "vue", java.util.List.of("src/App.vue"), java.util.List.of()));
```

并在文件顶部 import 区新增（若尚未导入）：
```java
import com.lingmaforge.backend.workbench.service.ProjectService;
import com.lingmaforge.backend.workbench.service.SandboxService;
```

- [ ] **Step 3: 新增场景四（迭代 NoOp 降级）**

在 `IterateSuccessScenario` 之后新增：

```java
    /**
     * 场景四：迭代修改 NoOp 降级。
     *
     * <p>使用真实 Spring 上下文（无 API Key → NoOpModel）。意图识别在 IntentDetectionNode
     * 被捕获兜底为 CHAT（NoOpModel 抛异常）→ 路由到 chat_reply 而非 delegate_iterate。
     * 故此场景实际验证的是"NoOp 降级时 modify_code 意图根本不会触发"——IntentDetectionNode
     * 的异常兜底会把任何意图都回退为 CHAT。</p>
     *
     * <p>但若要验证"delegate_iterate 节点内 modify 抛异常的兜底"，需让意图识别返回
     * MODIFY_CODE 但 IterationAgent 用 NoOpModel。这在真实 Spring 上下文中无法做到
     * （NoOpModel 会让意图识别先失败）。故此场景验证端到端降级：无 API Key 时整条
     * 对话链路不崩溃，最终回退为 CHAT 闲聊失败路径。</p>
     */
    @Nested
    @DisplayName("场景四：迭代修改 NoOp 降级（回退为闲聊失败）")
    @SpringBootTest
    class IterateNoOpFallbackScenario {

        @Autowired private ApplicationContext context;

        @Test
        @DisplayName("无 API Key → 意图识别兜底 CHAT → 闲聊 NoOp 失败 → 图不崩溃")
        void shouldFallbackToChatOnNoOpModel() {
            DialogRouter router = context.getBean(DialogRouter.class);
            GenerationStreamRegistry registry = context.getBean(GenerationStreamRegistry.class);
            CapturingEmitter emitter = new CapturingEmitter();
            String dialogId = "test-dialog-iterate-noop";
            registry.register(dialogId, emitter);

            log.info("--- 场景四：迭代修改 NoOp 降级 ---");
            Map<String, Object> inputs = Map.of(
                    DialogState.DIALOG_ID, dialogId,
                    DialogState.USER_MESSAGE, "把首页改成蓝色",
                    DialogState.PROJECT_ID, "100");

            var finalState = router.getCompiledGraph().stream(inputs)
                    .stream()
                    .reduce((first, second) -> second)
                    .orElseThrow()
                    .state();

            // NoOpModel 让意图识别异常 → 兜底 CHAT → 路由到 chat_reply → 闲聊 NoOp 失败
            assertThat(finalState.intent()).hasValue(DialogIntent.CHAT);
            assertThat(finalState.delegateResult())
                    .as("降级时 DELEGATE_RESULT 应含失败")
                    .hasValueSatisfying(s -> assertThat(s).contains("失败"));
            assertThat(emitter.errors)
                    .as("应收到至少一个错误事件")
                    .isNotEmpty();

            log.info("[OK] 场景四：NoOp 降级，intent 兜底 CHAT, delegateResult='{}'",
                    finalState.delegateResult().orElse(""));
            registry.unregister(dialogId);
        }
    }
```

- [ ] **Step 4: 运行 ChatFlowIntegrationTest 验证绿**

Run:
```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge/lingmaForge-backend
export JAVA_HOME="/d/Develop/DevelopTool/StudyEnvironment/PhpWebStudy-Data/app/openjdk-21.0.9"
./mvnw -q -Dtest='ChatFlowIntegrationTest' test
```
Expected: BUILD SUCCESS，全部 4 场景通过（原 2 + 新 2）

- [ ] **Step 5: 提交**

```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge
git add lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/dialog/ChatFlowIntegrationTest.java
git commit -m "test(dialog): ChatFlowIntegrationTest 新增迭代成功 + NoOp 降级两场景"
```

---

## Task 7: 全量回归 + 更新开发清单

**Files:**
- Modify: `项目文档/迭代文档/4.灵码工坊-智能体协同开发清单.md`

- [ ] **Step 1: 全量测试（排除 ModelConnectivityTest）**

Run:
```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge/lingmaForge-backend
export JAVA_HOME="/d/Develop/DevelopTool/StudyEnvironment/PhpWebStudy-Data/app/openjdk-21.0.9"
./mvnw -q -Dtest='!ModelConnectivityTest' test
```
Expected: BUILD SUCCESS，全部测试通过（原 145 + 新增 DelegateIterateNodeTest 7 + ChatFlowIntegrationTest 新增 2 = 154）

- [ ] **Step 2: 更新开发清单**

打开 `项目文档/迭代文档/4.灵码工坊-智能体协同开发清单.md`，把 §5 Phase 3 的 5-1 ~ 5-4 逐项勾选，补完成日期与产出文件；§1 阶段进度总览表 Phase 3 状态改为 ✅；§10 变更日志追加一条。

- [ ] **Step 3: 推送到 GitHub**

```bash
cd D:/Develop/Code_Projects/ai-java/LingmaForge
git add 项目文档/迭代文档/4.灵码工坊-智能体协同开发清单.md
git commit -m "docs: Phase 3 完成状态回写开发清单"
git push origin master
```

---

## 验证方式

1. `./mvnw -q -Dtest='!ModelConnectivityTest' test` 全绿（154 例）
2. 新增 `DelegateIterateNodeTest` 7 例 + `ChatFlowIntegrationTest` 新增 2 场景全绿
3. 现有 145 例无回归
4. 日志中能看到 `intent_detection → delegate_iterate → END`，SSE 事件序列含 `node_start` / `file`（patchFile）/ `node_end` / `complete` 或 `error`

## 不做的事（留给后续 Phase）
- ❌ 不桥接 CodeGenPipeline（Phase 4，DelegateCodegenNode 仍是桩）
- ❌ 不做回退重试（失败只写摘要 + error 事件）
- ❌ 不做流式 token 推送（IterationAgent.modify 是同步阻塞返回 String）
- ❌ 不动前端（Phase 5）
