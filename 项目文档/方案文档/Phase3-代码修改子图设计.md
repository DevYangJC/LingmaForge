# Phase 3 — 代码修改子图（接 IterationAgent）设计

> 状态：设计稿（待用户审阅 → writing-plans 生成实现计划）
> 日期：2026-08-12
> 关联清单：`项目文档/迭代文档/4.灵码工坊-智能体协同开发清单.md` §5

---

## 1. 目标与范围

### 1.1 目标

用户在对话中说"把首页改成蓝色"，意图识别为 `modify_code` → 路由到 `delegate_iterate` → 调用已有 `IterationAgent` 做增量补丁修改 → 单次构建验证 → 结果经 SSE 推前端 → 落库 assistant 摘要。

### 1.2 范围（用户已确认的 4 项决策）

| 决策点 | 选择 | 含义 |
|--------|------|------|
| 5-3 构建验证 | **最小闭环**：调 IterationAgent 修改 + 单次构建验证，不回退 | 不引入迭代子 StateGraph，不加重试回退 |
| projectId 来源 | **复用会话创建时的 projectId** | `ChatService.createDialog` 时写入 `DialogState.PROJECT_ID`，DelegateIterateNode 直接取用 |
| SSE 事件 | **复用 GenerationContext + 工具自发 SSE** | DelegateIterateNode 设 `GenerationContext`，工具内部 `emitFile`/`emitNode` 推 SSE |
| stop 停止 | **节点入口检查，modify 期间不可中断** | `execute` 开始前查 `isStopRequested`，modify 开始后等其跑完 |

### 1.3 不做的事

- ❌ 不引入迭代子 StateGraph（方案 C 已否决）
- ❌ 不抽公共构建验证服务（方案 B 已否决，避免动 Phase 0 固化的 `BuildVerificationNode`）
- ❌ 不做回退重试（失败只写摘要 + error 事件）
- ❌ 不动 `DialogRouter` 图结构（路由已接好，只改节点实现）
- ❌ 不动 `DialogState` channel（PROJECT_ID / DELEGATE_RESULT 已存在）
- ❌ 不做流式 token 推送（IterationAgent.modify 是同步阻塞返回 String，非 TokenStream）
- ❌ 不动前端（Phase 5）

---

## 2. 方案选择

### 方案 A（已选）：DelegateIterateNode 内联编排

`DelegateIterateNode.execute()` 内部顺序执行：
1. 入口校验（projectId / 停止标志）
2. 设 `GenerationContext(projectId, dialogId, emitter)`
3. 调 `IterationAgent.modify(fullPrompt)` —— 工具自发 SSE
4. 调 `SandboxService.npmBuild(projectId)` 单次构建验证
5. 根据构建结果写 `DELEGATE_RESULT` 摘要 + 推 complete/error 事件
6. `finally` 清理 `GenerationContext`

**选择理由**：4 项决策全部指向内联编排；构建验证逻辑重复极小（一次 `npmBuild` + 写摘要），不值得为此动 Phase 0 固化节点；与 `GenerationService.runIteration` 几乎同构，可直接借鉴。

### 方案 B（已否决）：抽公共构建验证服务

把 `BuildVerificationNode` 的构建逻辑抽到不依赖 CodeGenState 的服务方法。Phase 3 范围扩大，要动 Phase 0 固化的节点，回归风险高；YAGNI。

### 方案 C（已否决）：新建迭代子 StateGraph

与"最小闭环、不回退"决策矛盾，过度设计；DialogState 被构建相关字段污染。

---

## 3. 组件设计

### 3.1 DelegateIterateNode 改造

**文件**：`workbench/ai/dialog/DelegateIterateNode.java`（修改，从桩 → 真实）

**构造注入**（新增 4 个依赖）：

```java
public DelegateIterateNode(
        AgentFactory agentFactory,
        GenerationStreamRegistry streamRegistry,
        SandboxService sandboxService,
        ProjectService projectService) {
    this.iterationAgent = agentFactory.createIterationAgent();
    this.streamRegistry = streamRegistry;
    this.sandboxService = sandboxService;
    this.projectService = projectService;
}
```

- `AgentFactory` —— 创建 IterationAgent（构造时一次性创建，与 ChatReplyNode 模式一致）
- `GenerationStreamRegistry` —— 按 dialogId 取 emitter + 查停止标志
- `SandboxService` —— 单次构建验证
- `ProjectService` —— 取项目上下文拼 prompt（`getProjectContext(projectId)`）

**execute 流程**：

```
execute(DialogState state):
  1. dialogId = state.dialogId()
     projectIdStr = state.projectId()
     userMessage = state.userMessage()
  2. 入口校验：
     - projectId 缺失 → 返回 DELEGATE_RESULT="[代码修改失败：未关联项目]"
     - emitter = streamRegistry.get(dialogId)，null → 返回 DELEGATE_RESULT="[代码修改失败：SSE 连接未建立]"
     - streamRegistry.isStopRequested(dialogId) → 返回 DELEGATE_RESULT="[代码修改已取消]"
  3. GenerationContext.set(Long.valueOf(projectIdStr), dialogId, emitter)
  4. try:
       a. emitter.emitNodeStart(NODE_NAME, "正在理解修改意图并定位代码...")
       b. contextSummary = buildIterationContext(projectId)  // 调 projectService.getProjectContext
       c. fullPrompt = "用户修改指令: " + userMessage + "\n\n项目上下文:\n" + contextSummary
       d. agentReply = iterationAgent.modify(fullPrompt)     // 工具内部自发 emitFile/emitNode
       e. emitter.emitNodeEnd(NODE_NAME)
       f. 单次构建验证：
          buildResult = sandboxService.npmBuild(projectId)
          if buildResult.status() == SUCCESS:
            summary = "修改已完成，构建通过（耗时 Xs）。\n\n" + agentReply
            emitter.complete("", 0, buildResult.durationMillis/1000)
          else:
            summary = "修改已应用，但构建失败：\n" + truncate(buildResult.error(), 500) + "\n\n" + agentReply
            emitter.error("构建失败: " + truncate(buildResult.error(), 200))
       g. log.info("[delegate_iterate] 完成: dialogId={}, buildStatus={}", dialogId, buildResult.status())
       h. return Map.of(DELEGATE_RESULT, summary)
     catch Exception e:
       log.error("[delegate_iterate] 失败: dialogId={}", dialogId, e)
       emitter.error("代码修改失败: " + e.getMessage())
       return Map.of(DELEGATE_RESULT, "[代码修改失败]")
     finally:
       GenerationContext.clear()
```

**关键设计点**：

1. **GenerationContext 生命周期完全包在 try-finally 内**——`set` 在 try 前、`clear` 在 finally。`node_async` 派发到 fork-join 线程后，ThreadLocal 在该线程内有效，工具在同一线程被 LangChain4j 同步调用，能正确取到。
2. **构建验证失败不回退**——只写失败摘要到 DELEGATE_RESULT，推 error 事件，不重新调 modify。与用户决策一致。
3. **agentReply 始终拼入摘要**——无论构建成功失败，IterationAgent 的文本回复都拼进 DELEGATE_RESULT，供落库与前端展示。
4. **buildError 截断**——`npmBuild` 的 error 可能很长（含完整 npm 日志），摘要里截断到 500 字符，error 事件里截断到 200 字符，避免 SSE payload 过大。
5. **emitter.emitNodeStart/emitNodeEnd**——给前端"正在修改..."的进度可视化，与 GenerationService.runIteration 的 `emitNode` 对齐（但用 node_start/node_end 更精确）。

### 3.2 buildIterationContext 辅助方法

```java
private String buildIterationContext(Long projectId) {
    var ctx = projectService.getProjectContext(projectId);
    return "框架: " + ctx.framework() + "\n文件列表:\n" + String.join("\n", ctx.filePaths());
}
```

直接借鉴 `GenerationService.buildIterationContext`（第 292-295 行），不抽公共方法（YAGNI，只有两个调用方，且 GenerationService 那个是 private）。

### 3.3 DialogRouter / DialogState 不改动

- `DialogRouter` 的 `routeByIntent` 已把 `MODIFY_CODE → delegate_iterate`，条件边已接好，不动。
- `DialogState.PROJECT_ID`（String 形式）与 `DELEGATE_RESULT` 已存在，不动。
- `DialogState` 不增 `BUILD_STATUS` / `BUILD_ERROR` / `RETRY_COUNT`——构建结果只写进 DELEGATE_RESULT 摘要，不污染对话状态。

### 3.4 ChatService 不改动

- `ChatService.sendMessage` 已把 `dialog.getProjectId()` 写入 `DialogState.PROJECT_ID`（第 219-221 行），DelegateIterateNode 直接取用。
- `ChatService.runDialog` 在图执行完毕后落库 assistant 回复（第 234-240 行），对非 chat 意图推 `emitter.complete("", 0, 0)` 兜底（第 242-244 行）。
- **潜在重复 complete 问题**：DelegateIterateNode 在构建成功时已推 `emitter.complete(...)`，ChatService.runDialog 对非 chat 意图又会推一次 `emitter.complete("", 0, 0)`。`SseStreamEmitter.complete` 的 `send` 方法吞 IOException/IllegalStateException，重复调用不会抛异常，但会推两个 complete 事件给前端。

  **处理**：ChatService.runDialog 的"非 chat 意图推 complete 兜底"逻辑需要调整——改为"非 chat 且非 modify 意图才推 complete 兜底"，或更稳妥地"只在 DELEGATE_RESULT 为空时推兜底"。推荐后者，因为它是按结果而非意图类型判断，更稳健。

### 3.5 ChatService.runDialog 兜底逻辑调整

**文件**：`workbench/service/ChatService.java`（修改）

当前逻辑（第 234-245 行）：
```java
if (last != null) {
    String reply = last.state().delegateResult().orElse("");
    DialogIntent intent = last.state().intent().orElse(DialogIntent.CHAT);
    if (reply != null && !reply.isBlank()) {
        saveChatMessage(projectId, dialogId, "assistant", reply);
    }
    if (intent != DialogIntent.CHAT) {
        emitter.complete("", 0, 0);  // 非 chat 意图：桩节点结果
    }
}
```

调整为：
```java
if (last != null) {
    String reply = last.state().delegateResult().orElse("");
    if (reply != null && !reply.isBlank()) {
        saveChatMessage(projectId, dialogId, "assistant", reply);
    }
    // 只有 delegate 节点没自己推过 complete 时才兜底。
    // Phase 2 闲聊：ChatReplyNode 已推 emitChatComplete（不是 complete 事件）；
    // Phase 3 迭代：DelegateIterateNode 已推 complete/error。
    // 故这里只在 reply 为空（异常情况）时推 complete 兜底，避免前端无限等待。
    if (reply == null || reply.isBlank()) {
        emitter.complete("", 0, 0);
    }
}
```

**理由**：Phase 2 闲聊走 `emitChatComplete`（message 事件带 complete 标志），不走 `complete` 事件；Phase 3 迭代走 `complete`/`error` 事件。两种情况 delegate 节点都自处理了完成信号，ChatService 不应再重复推。只在 reply 为空（delegate 节点异常未写 DELEGATE_RESULT）时兜底。

---

## 4. 数据流

```
用户消息 "把首页改成蓝色"
  ↓
ChatService.sendMessage(dialogId, msg)
  - 查 DialogEntity → projectId
  - 落库 user 消息
  - 建 SSE + SseStreamEmitter
  - 注册到 streamRegistry + streamContextMap
  - 心跳
  - executor.execute(runDialog)
  ↓
runDialog 驱动 DialogRouter.stream(inputs)
  inputs = {DIALOG_ID, USER_MESSAGE, PROJECT_ID}
  ↓
intent_detection → intent=MODIFY_CODE
  ↓ 条件边
delegate_iterate.execute(state)
  - projectId = Long.valueOf(state.projectId())
  - emitter = streamRegistry.get(dialogId)
  - GenerationContext.set(projectId, dialogId, emitter)
  - emitNodeStart("delegate_iterate", "正在理解修改意图...")
  - IterationAgent.modify(fullPrompt)
      ↓ 工具自发 SSE
      - readProjectContext / readFileContext（读上下文）
      - searchCode（定位）
      - patchFile / writeFile（应用补丁，emitter.emitFile）
  - emitNodeEnd("delegate_iterate")
  - sandboxService.npmBuild(projectId)
      ↓
      BuildResult{status, output, error, durationMillis}
  - 拼 summary（含 agentReply + 构建结果）
  - 构建成功 → emitter.complete("", 0, buildSeconds)
    构建失败 → emitter.error("构建失败: ...")
  - return {DELEGATE_RESULT: summary}
  - finally GenerationContext.clear()
  ↓ END
runDialog 收到最后 NodeOutput
  - 落库 assistant 消息（summary）
  - reply 非空 → 不推兜底 complete
  - finally emitter.safeComplete() + cleanup
  ↓
SSE 连接关闭，前端收到完整事件序列
```

---

## 5. 错误处理

| 失败场景 | 处理 | DELEGATE_RESULT | SSE |
|----------|------|-----------------|-----|
| projectId 缺失（闲聊会话误路由） | 入口校验直接返回 | `[代码修改失败：未关联项目]` | 无额外事件 |
| emitter 缺失 | 入口校验直接返回 | `[代码修改失败：SSE 连接未建立]` | 无 |
| 用户已点停止 | 入口校验直接返回 | `[代码修改已取消]` | 无 |
| IterationAgent.modify 抛异常（NoOpModel / LLM 失败） | try-catch 兜底 | `[代码修改失败]` | `emitter.error("代码修改失败: ...")` |
| npmBuild 抛异常 | sandboxService 内部已 catch，返回 `BuildResult(FAILED, ..., e.getMessage(), ...)`；DelegateIterateNode 正常走失败摘要路径 | `修改已应用，但构建失败：...` | `emitter.error("构建失败: ...")` |
| 构建失败（npm 退出码非 0） | 正常路径，写失败摘要 | `修改已应用，但构建失败：...` | `emitter.error("构建失败: ...")` + `complete` 不推（error 已推） |
| GenerationContext 泄漏 | finally `GenerationContext.clear()` 保证清理 | — | — |

**注意**：构建失败时，`emitter.error(...)` 推了 error 事件，**不再推** `emitter.complete(...)`。前端收到 error 事件即应终止流。ChatService.runDialog 的 `finally` 会调 `emitter.safeComplete()`，但此时 SSE 可能已关闭，`safeComplete` 吞异常。

---

## 6. 测试策略

### 6.1 DialogRouterTest 适配

`DelegateIterateNode` 构造签名变了（新增 4 个依赖），`DialogRouterTest.setUp` 需更新：
- `@Mock SandboxService sandboxService`
- `@Mock ProjectService projectService`
- `new DelegateIterateNode(agentFactory, streamRegistry, sandboxService, projectService)`
- 端到端测试里 `modify_code` 意图的桩节点现在会调真实 `iterationAgent.modify()`，需 mock `AgentFactory.createIterationAgent()` 返回一个返回固定字符串的 mock agent。

### 6.2 DelegateIterateNode 单测（新增）

**文件**：`src/test/.../ai/dialog/DelegateIterateNodeTest.java`

`@ExtendWith(MockitoExtension.class)`，mock AgentFactory / GenerationStreamRegistry / SandboxService / ProjectService。

测试组：
1. **projectId 缺失** → DELEGATE_RESULT 含"未关联项目"，不调 modify
2. **emitter 缺失** → DELEGATE_RESULT 含"SSE 连接未建立"，不调 modify
3. **已请求停止** → DELEGATE_RESULT 含"已取消"，不调 modify
4. **modify 成功 + 构建成功** → DELEGATE_RESULT 含 agentReply + "构建通过"，verify emitter.complete 被调
5. **modify 成功 + 构建失败** → DELEGATE_RESULT 含"构建失败"，verify emitter.error 被调
6. **modify 抛异常** → DELEGATE_RESULT = "[代码修改失败]"，verify emitter.error 被调
7. **GenerationContext 清理** → modify 抛异常后 `GenerationContext.get()` 应抛 IllegalStateException（验证 finally clear 生效）

### 6.3 集成测试（扩展 ChatFlowIntegrationTest）

在 `ChatFlowIntegrationTest` 新增第三个 `@Nested` 场景：

**场景三：迭代修改端到端（Mock 模型 + 构建成功）**
- `@SpringBootTest` + `@Import(StubIterateAgentConfig.class)`（新建，仿 StubChatAgentConfig，在 `com.lingmaforge.testconfig` 包外）
- mock AgentFactory.createIterationAgent() 返回固定 "修改完成" 的 mock agent
- mock SandboxService.npmBuild 返回 `BuildResult(SUCCESS, ...)`
- 驱动图：inputs 带 PROJECT_ID + USER_MESSAGE="把首页改成蓝色"
- 断言：intent=MODIFY_CODE / delegateResult 含"修改完成" + "构建通过" / emitter 收到 complete 事件 / errors 空

**场景四：迭代修改 NoOp 降级**
- plain `@SpringBootTest`（真实 AgentFactory + NoOpModel）
- PROJECT_ID 设一个真实项目 ID（test profile 有 schema.sql 初始化数据，或测试内 insert）
- 断言：intent=MODIFY_CODE / delegateResult 含"失败" / errors 非空 / 图不崩溃

### 6.4 现有测试无回归

- `DialogRouterTest` 12 例适配后仍全绿
- `ChatFlowIntegrationTest` 原 2 场景仍全绿
- `ChatControllerTest` 6 例不受影响（Controller 层无改动）
- 全量 `mvnw test`（JDK 21）BUILD SUCCESS

---

## 7. 产出文件清单

**新建（2 个）**：
1. `src/test/.../ai/dialog/DelegateIterateNodeTest.java`
2. `src/test/.../testconfig/StubIterateAgentConfig.java`（扫描范围外，供集成测试导入）

**修改（4 个）**：
1. `workbench/ai/dialog/DelegateIterateNode.java`（桩 → 真实桥接）
2. `workbench/service/ChatService.java`（runDialog 兜底 complete 逻辑调整）
3. `src/test/.../ai/dialog/DialogRouterTest.java`（适配 DelegateIterateNode 新构造：`setUp` 与 `routerWithAnalyzer` 两处 `new DelegateIterateNode()` 都要改）
4. `src/test/.../ai/dialog/ChatFlowIntegrationTest.java`（新增迭代场景）

**不动**：
- `DialogRouter.java` / `DialogState.java` / `DialogIntent.java`
- `AgentFactory.java` / `AgentType.java`（IterationAgent 已在 Phase 0 就绪）
- `IterationTools.java` / `FileTools.java` / `ProjectContextTools.java`
- `SandboxService.java` / `BuildVerificationNode.java`
- `ChatController.java` / DTO
- 前端

---

## 8. 验证方式

1. `mvnw test -pl lingmaForge-backend`（JDK 21）全绿，排除预存 `ModelConnectivityTest`
2. 新增 `DelegateIterateNodeTest` 7 例 + `ChatFlowIntegrationTest` 新增 2 场景全绿
3. 现有 145 例无回归
4. 日志中能看到 `intent_detection → delegate_iterate → END`，SSE 事件序列含 `node_start` / `file`（patchFile）/ `node_end` / `complete` 或 `error`

---

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| GenerationContext 在 fork-join 线程泄漏 | try-finally `clear()`；`DelegateIterateNodeTest` 验证异常后清理 |
| 重复 complete 事件（DelegateIterateNode + ChatService 各推一次） | ChatService.runDialog 兜底逻辑改为"reply 为空才推" |
| npmBuild 耗时长（最多 180s），用户 modify 期间不可中断 | 节点入口检查停止；构建超时由 `SandboxService` 的 `buildTimeoutSeconds` 控制 |
| 构建失败 error 日志过长撑爆 SSE payload | 摘要截断 500 字符，error 事件截断 200 字符 |
| NoOpModel 降级时 modify 抛 IllegalStateException | try-catch 兜底，与 ChatReplyNode 同模式 |
| StubIterateAgentConfig 污染其他 @SpringBootTest | 放 `com.lingmaforge.testconfig`（ComponentScan 范围外），Phase 2 已验证此模式 |
