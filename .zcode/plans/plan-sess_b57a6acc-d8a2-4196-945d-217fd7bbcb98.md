# Phase 1 — 对话入口层 DialogRouter 实现计划

## 决策回顾（用户已确认）
- **三个 delegate 节点先建桩**：Phase 1 只建图骨架 + 意图识别 + 路由，三个 delegate 节点（delegate_codegen / delegate_iterate / chat_reply）返回占位状态；Phase 2-4 再逐个填充真实逻辑。
- **新建 DialogEntity（会话维度）**：不复用 projectId 当会话 key，新建 `lf_dialog` 表 + `DialogEntity` + `DialogMapper`。

## 范围说明
Phase 1 的交付物是**可编译、可测试的对话图骨架**，不包含 SSE Controller、闲聊流式回复、代码生成桥接的真实逻辑（这些分别在 Phase 2/3/4）。本阶段目标：意图识别能跑通、图能编译、三条路由可被单测验证、Spring 容器能加载 DialogRouter Bean。

---

## 步骤 1：DialogState（对话状态机）

**新建文件**：`workbench/ai/dialog/DialogState.java`

仿照 `CodeGenState` 模式（`extends AgentState`，构造 `Map<String,Object>`，静态 `channels()` 工厂，`Optional<T>` getter）。

**Channel 常量与合并策略**：

| 常量 | 类型 | 合并策略 | 说明 |
|------|------|----------|------|
| `DIALOG_ID` | String | nullableChannel() | 会话 ID（Phase 2 用，Phase 1 预留） |
| `PROJECT_ID` | String | nullableChannel() | 关联项目 ID（可空，闲聊无项目） |
| `USER_MESSAGE` | String | nullableChannel() | 当前轮用户原始消息 |
| `MESSAGES` | List<DialogMessage> | appender(ArrayList) | 对话历史（追加合并，Phase 1 预留，Phase 2 填充） |
| `INTENT` | DialogIntent | nullableChannel() | 识别出的意图 |
| `INTENT_CONFIDENCE` | Double | nullableChannel() | 意图置信度 |
| `DELEGATE_RESULT` | String | nullableChannel() | delegate 节点的返回结果（Phase 1 桩节点写入占位文本） |

**新增 record**：`workbench/ai/dialog/DialogMessage.java`（role: String, content: String），implements Serializable。Phase 1 暂不填充 MESSAGES（意图识别只看 USER_MESSAGE），但预留 channel 避免后续改 state 结构。

---

## 步骤 2：DialogIntent 枚举

**新建文件**：`workbench/ai/dialog/DialogIntent.java`

```java
public enum DialogIntent {
    GENERATE_PROJECT("generate_project"),  // 生成项目
    MODIFY_CODE("modify_code"),            // 修改代码
    CHAT("chat");                          // 闲聊
    // private final String type; + getter + fromType(String)
}
```

---

## 步骤 3：IntentAnalyzer AiService（结构化输出）

**新建文件**：`workbench/ai/service/IntentAnalyzer.java`

仿照 `RequirementAnalyzer` 模式：
```java
public interface IntentAnalyzer {
    @UserMessage("{{userMessage}}")
    IntentResult analyze(@V("userMessage") String userMessage);
}
```

**新建 record**：`workbench/ai/dialog/IntentResult.java`（intent: String, confidence: double），implements Serializable。用 String 而非枚举，便于模型输出 + `DialogIntent.fromType()` 转换。

---

## 步骤 4：AgentType + AgentFactory 扩展

**修改**：`workbench/ai/factory/AgentType.java` — 新增枚举常量：
```java
INTENT_ANALYSIS(6, "意图识别", "intent-detection"),
```

**修改**：`workbench/ai/factory/AgentFactory.java` — 新增方法：
```java
public IntentAnalyzer createIntentAnalyzer() {
    return AiServices.builder(IntentAnalyzer.class)
            .chatModel(resolveModel(AgentType.INTENT_ANALYSIS))
            .systemMessageProvider(id -> promptLoader.loadSystemPrompt(AgentType.INTENT_ANALYSIS.getType()))
            .build();
}
```
用 `deepseek-flash`（便宜模型）。`resolveModel` 已支持未知 agentType 回退到第一个可用模型，无需改 resolve 逻辑。

**修改**：`application-dev.yml` 的 `lingma.agents` 增加：
```yaml
intent-detection:
  model: deepseek-flash
```

**新建 prompt**：`resources/prompts/intent-detection-system.txt` — 指导模型做三分类（generate_project / modify_code / chat），输出结构化结果。明确三类定义：
- `generate_project`：用户要新建/生成/搭建一个项目或应用
- `modify_code`：用户要修改/调整/优化已有代码（提到具体文件、页面、样式改动）
- `chat`：问候、技术问答、闲聊、概念解释等不涉及具体代码操作

---

## 步骤 5：IntentDetectionNode（意图识别节点）

**新建文件**：`workbench/ai/dialog/IntentDetectionNode.java`

- `@Component`，构造注入 `AgentFactory`
- `public static final String NODE_NAME = "intent_detection"`
- `public Map<String,Object> execute(DialogState state)`：
  1. 取 `state.userMessage()`
  2. 调 `agentFactory.createIntentAnalyzer().analyze(userMessage)`
  3. 解析 `IntentResult`，用 `DialogIntent.fromType()` 转枚举；未匹配或异常时回退为 `CHAT`（最安全）
  4. 返回 `Map.of(INTENT, dialogIntent, INTENT_CONFIDENCE, confidence)`
  5. 失败兜底：catch 异常 → intent=CHAT, confidence=0.0（保证图不中断）

---

## 步骤 6：三个 Delegate 桩节点

三个节点都是 `@Component`，各有一个 `execute(DialogState)` 返回占位状态。

**新建文件**：
1. `workbench/ai/dialog/DelegateCodegenNode.java`
   - `NODE_NAME = "delegate_codegen"`
   - `execute` → `Map.of(DELEGATE_RESULT, "[桩] 已识别为生成项目意图，桥接逻辑将在 Phase 4 实现")`

2. `workbench/ai/dialog/DelegateIterateNode.java`
   - `NODE_NAME = "delegate_iterate"`
   - `execute` → `Map.of(DELEGATE_RESULT, "[桩] 已识别为代码修改意图，桥接逻辑将在 Phase 3 实现")`

3. `workbench/ai/dialog/ChatReplyNode.java`
   - `NODE_NAME = "chat_reply"`
   - `execute` → `Map.of(DELEGATE_RESULT, "[桩] 已识别为闲聊意图，流式回复将在 Phase 2 实现")`

桩节点不注入任何依赖，纯返回 Map，保证 Phase 1 图可独立编译运行。

---

## 步骤 7：DialogRouter 图编排

**新建文件**：`workbench/ai/dialog/DialogRouter.java`

仿照 `CodeGenPipeline` 模式（`@Component`，`@PostConstruct init()`，`getCompiledGraph()`）。

**图结构**：
```
START → intent_detection → [conditional edges] → delegate_codegen / delegate_iterate / chat_reply → END
```

**关键实现**：
```java
@PostConstruct
public void init() throws Exception {
    StateGraph<DialogState> graph = new StateGraph<>(DialogState.channels(), DialogState::new)
            .addNode(IntentDetectionNode.NODE_NAME, node_async(intentDetectionNode::execute))
            .addNode(DelegateCodegenNode.NODE_NAME, node_async(delegateCodegenNode::execute))
            .addNode(DelegateIterateNode.NODE_NAME, node_async(delegateIterateNode::execute))
            .addNode(ChatReplyNode.NODE_NAME, node_async(chatReplyNode::execute));

    graph.addEdge(START, IntentDetectionNode.NODE_NAME);

    graph.addConditionalEdges(IntentDetectionNode.NODE_NAME,
            edge_async(this::routeByIntent),
            Map.of(
                DelegateCodegenNode.NODE_NAME, DelegateCodegenNode.NODE_NAME,
                DelegateIterateNode.NODE_NAME, DelegateIterateNode.NODE_NAME,
                ChatReplyNode.NODE_NAME, ChatReplyNode.NODE_NAME));

    graph.addEdge(DelegateCodegenNode.NODE_NAME, END);
    graph.addEdge(DelegateIterateNode.NODE_NAME, END);
    graph.addEdge(ChatReplyNode.NODE_NAME, END);

    this.compiledGraph = graph.compile();
}

// public 供单测直接调用
public String routeByIntent(DialogState state) {
    DialogIntent intent = state.intent().orElse(DialogIntent.CHAT);
    return switch (intent) {
        case GENERATE_PROJECT -> DelegateCodegenNode.NODE_NAME;
        case MODIFY_CODE     -> DelegateIterateNode.NODE_NAME;
        case CHAT            -> ChatReplyNode.NODE_NAME;
    };
}
```

构造函数注入：`IntentDetectionNode`、`DelegateCodegenNode`、`DelegateIterateNode`、`ChatReplyNode`（4 个节点 bean）。无 streamRegistry 依赖（Phase 1 不接 SSE）。

---

## 步骤 8：DialogEntity + DialogMapper + schema.sql（会话维度实体）

按用户决策新建会话级实体，Phase 1 先建表与实体，Phase 2 再用。

**新建文件**：`workbench/entity/DialogEntity.java`
- `@TableName("lf_dialog")`
- 字段：`Long id`(@TableId ASSIGN_ID)、`String dialogId`(VARCHAR(64)，业务 ID，=SSE streamId)、`Long projectId`(可空)、`String title`(会话标题，可空)、`String status`(默认 active)、`LocalDateTime createdAt`、`LocalDateTime updatedAt`

**新建文件**：`workbench/mapper/DialogMapper.java` — `extends BaseMapper<DialogEntity>`

**修改**：`schema.sql` 追加：
```sql
CREATE TABLE IF NOT EXISTS lf_dialog (
    id              BIGINT PRIMARY KEY,
    dialog_id       VARCHAR(64)  NOT NULL,
    project_id      BIGINT,
    title           VARCHAR(200),
    status          VARCHAR(16)  NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_dialog_id UNIQUE (dialog_id)
);
CREATE INDEX IF NOT EXISTS idx_dialog_project ON lf_dialog (project_id);
```

> 注：Phase 1 只建表 + 实体 + Mapper，不写 Service/Controller（留到 Phase 2）。这样 Phase 2 对话持久化能直接用，且不破坏现有 `lf_chat_message`（后续 Phase 2 可给 `lf_chat_message` 加 `dialog_id` 列）。

---

## 步骤 9：DialogRouter 单测

**新建文件**：`src/test/java/com/lingmaforge/backend/ai/dialog/DialogRouterTest.java`

聚焦路由逻辑（不测真实意图识别，mock IntentResult）。

**测试组**：
1. **routeByIntent 三路径**（直接调 `router.routeByIntent(state)`，构造含不同 INTENT 的 DialogState）：
   - `GENERATE_PROJECT` → 返回 `"delegate_codegen"`
   - `MODIFY_CODE` → 返回 `"delegate_iterate"`
   - `CHAT` → 返回 `"chat_reply"`
   - intent 缺失（Optional.empty）→ 回退 `"chat_reply"`

2. **图结构完整性**（`@SpringBootTest` 或直接 new）：
   - 断言 `compiledGraph != null`
   - 断言 mermaid 图字符串包含 4 个节点名

3. **IntentDetectionNode 兜底**（mock AgentFactory 返回抛异常的 IntentAnalyzer）：
   - 调用 `execute`，断言返回 `intent=CHAT, confidence=0.0`

---

## 步骤 10：扩展 AiFrameworkConfigurationTests

**修改**：`src/test/java/com/lingmaforge/backend/AiFrameworkConfigurationTests.java`

追加 `ObjectProvider<DialogRouter>` 注入，在现有测试方法中增加断言：
```java
DialogRouter dialogRouter = dialogRouterProvider.getIfAvailable();
assertThat(dialogRouter).isNotNull();
assertThat(dialogRouter.getCompiledGraph()).isNotNull();
log.info("[OK] DialogRouter Bean 存在");
log.info("  DialogGraph: {}", dialogRouter.getCompiledGraph().getGraph(...MERMAID));
```

---

## 步骤 11：更新开发清单文档

**修改**：`项目文档/迭代文档/4.灵码工坊-智能体协同开发清单.md`
- §3 Phase 1 的 1-1 ~ 1-6 逐项勾选，补完成日期与产出文件
- §9 关键文件索引追加 `workbench/ai/dialog/` 入口
- §10 变更日志追加一条
- §1 阶段进度总览表 Phase 1 状态改为 ✅

---

## 新增/修改文件清单

**新建（11 个）**：
1. `workbench/ai/dialog/DialogState.java`
2. `workbench/ai/dialog/DialogIntent.java`
3. `workbench/ai/dialog/DialogMessage.java`
4. `workbench/ai/dialog/IntentResult.java`
5. `workbench/ai/dialog/IntentDetectionNode.java`
6. `workbench/ai/dialog/DelegateCodegenNode.java`
7. `workbench/ai/dialog/DelegateIterateNode.java`
8. `workbench/ai/dialog/ChatReplyNode.java`
9. `workbench/ai/dialog/DialogRouter.java`
10. `workbench/ai/service/IntentAnalyzer.java`
11. `workbench/entity/DialogEntity.java` + `workbench/mapper/DialogMapper.java`
12. `src/test/.../ai/dialog/DialogRouterTest.java`
13. `resources/prompts/intent-detection-system.txt`

**修改（4 个）**：
1. `workbench/ai/factory/AgentType.java`（+INTENT_ANALYSIS）
2. `workbench/ai/factory/AgentFactory.java`（+createIntentAnalyzer）
3. `application-dev.yml`（+intent-detection agent 配置）
4. `schema.sql`（+lf_dialog 表）
5. `AiFrameworkConfigurationTests.java`（+DialogRouter 断言）
6. `4.灵码工坊-智能体协同开发清单.md`（状态回写）

---

## 验证方式
1. `mvnw test -pl lingmaForge-backend` 全绿（含新增 DialogRouterTest + 扩展的 AiFrameworkConfigurationTests）
2. 现有 75 个测试不回归
3. 日志中能看到 DialogRouter 图编译成功 + Mermaid 图含 4 节点

## 不做的事（留给后续 Phase）
- ❌ 不建 ChatController / /api/chat/* 接口（Phase 2）
- ❌ 不接 SSE 流式（Phase 2）
- ❌ 不做闲聊真实回复（Phase 2，ChatReplyNode 现在是桩）
- ❌ 不桥接 CodeGenPipeline（Phase 4，DelegateCodegenNode 现在是桩）
- ❌ 不桥接 IterationAgent（Phase 3，DelegateIterateNode 现在是桩）
- ❌ 不动前端（Phase 5）