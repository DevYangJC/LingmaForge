# Phase 2 — 闲聊子图 + /api/chat/* 接口 实现计划

## 决策回顾

Phase 1 已交付可编译的 DialogRouter 图骨架（commit 945e2af），三个 delegate 均为桩节点。Phase 2 把 **chat_reply** 这条最简单的路径跑通端到端：用户发消息 → 意图识别 → 路由到 chat_reply → StreamingChatModel 流式回复 → SSE 逐 token 推前端 → 完成后落库。

### 关键设计决策

1. **ChatReplyNode 同步阻塞消费 TokenStream**（仿 `CodeGenerationNode.generateOneFile()`）——在 `execute()` 内 `.onPartialResponse(token → emitter.emitChatToken(token))` + `CompletableFuture.join()` 阻塞到流结束，完整回复写入 `DELEGATE_RESULT`。整个节点执行对图引擎仍是同步的。

2. **ChatReplyNode 通过 GenerationStreamRegistry 获取 emitter**（NOT GenerationContext ThreadLocal）——DialogRouter 用 `node_async` 把节点派发到 fork-join 线程，ThreadLocal 不会传播。ChatService 以 `dialogId` 为 key 注册 emitter 到 `GenerationStreamRegistry`；ChatReplyNode 从 `DialogState.DIALOG_ID` 取 dialogId，调 `streamRegistry.get(dialogId)`。

3. **新增 emitChatToken / emitChatComplete 方法**到 `GenerationStreamEmitter` 接口——闲聊 token 是增量文本流，语义不同于 `emitNode`（进度快照）。方法在 `SseStreamEmitter` 中发送为 `"message"` 事件（事件名 `message`），与清单"闲聊用 message 事件"对齐；payload 用 `nodeName="chat_reply"` 区分。

4. **新增 AgentType.CHAT_REPLY + ChatReplyAgent + createChatReplyAgent()**——镜像 CodeGenAgent 的 TokenStream 模式，用 deepseek-flash（便宜模型）。

5. **ChatService 仿 GenerationService 线程模型**——SseEmitter(0L) → SseStreamEmitter 包裹 → 注册到 streamRegistry → 心跳 → 派发到 generationExecutor → 驱动 `dialogRouter.getCompiledGraph().stream(inputs)`。

6. **ChatController 仿 GenerationController**——SSE 端点直接返回 SseEmitter（不包裹 Result），非 SSE 端点返回 Result<>。

---

## 步骤 1：新增 ChatReplyAgent AiService 接口

**新建文件**：`workbench/ai/service/ChatReplyAgent.java`

镜像 `CodeGenAgent` 模式：
```java
public interface ChatReplyAgent {
    @UserMessage("{{userMessage}}")
    TokenStream reply(@V("userMessage") String userMessage);
}
```

无工具、流式返回 TokenStream。system prompt 由 AgentFactory 注入。

---

## 步骤 2：AgentType + AgentFactory 扩展

**修改**：`workbench/ai/factory/AgentType.java` — 新增枚举常量：
```java
CHAT_REPLY(7, "闲聊回复", "chat-reply"),
```

**修改**：`workbench/ai/factory/AgentFactory.java` — 新增方法（镜像 `createCodeGenAgent()`）：
```java
public ChatReplyAgent createChatReplyAgent() {
    return AiServices.builder(ChatReplyAgent.class)
            .streamingChatModel(resolveStreamingModel(AgentType.CHAT_REPLY))
            .systemMessageProvider(id -> promptLoader.loadSystemPrompt(AgentType.CHAT_REPLY.getType()))
            .build();
}
```

**修改**：`application-dev.yml` 的 `lingma.agents` 增加：
```yaml
chat-reply:
  model: deepseek-flash
```

**新建 prompt**：`resources/prompts/chat-reply-system.txt` — 闲聊回复 system prompt。风格仿 `intent-detection-system.txt`（中文、Markdown `##` 分段）。内容：灵码工坊的 AI 编程助手身份、友好简洁的回答风格、技术问题给出清晰解释、不编造不确定的信息。

---

## 步骤 3：GenerationStreamEmitter 接口扩展 + SseStreamEmitter 实现

**修改**：`workbench/ai/observer/GenerationStreamEmitter.java` — 新增两个方法：
```java
/**
 * 流式推送闲聊回复 Token。
 *
 * @param token 增量文本
 */
void emitChatToken(String token);

/**
 * 推送闲聊回复完成事件。
 *
 * @param fullResponse 完整回复文本
 */
void emitChatComplete(String fullResponse);
```

**修改**：`workbench/service/GenerationService.java` 的 `SseStreamEmitter` 内部类 — 实现两个新方法：
```java
@Override
public void emitChatToken(String token) {
    send("message", Map.of(
            "threadId", taskId,
            "nodeName", "chat_reply",
            "text", token,
            "textType", "TEXT",
            "error", false));
}

@Override
public void emitChatComplete(String fullResponse) {
    send("message", Map.of(
            "threadId", taskId,
            "nodeName", "chat_reply",
            "text", fullResponse,
            "textType", "TEXT",
            "error", false,
            "complete", true));
}
```

> 注：`SseStreamEmitter` 是 `GenerationService` 的 private static 内部类。ChatService 需要自己的 SSE emitter 实现（见步骤 5），或将 `SseStreamEmitter` 提取为独立类。**决策：提取为独立类** `workbench/ai/observer/SseStreamEmitter.java`（public），让 GenerationService 和 ChatService 共用，避免代码重复。

---

## 步骤 4：ChatReplyNode 流式改造

**修改**：`workbench/ai/dialog/ChatReplyNode.java`

从桩节点改造为真实流式回复节点。构造注入 `AgentFactory` + `GenerationStreamRegistry`。

```java
@Component
public class ChatReplyNode {

    public static final String NODE_NAME = "chat_reply";

    private final ChatReplyAgent chatReplyAgent;
    private final GenerationStreamRegistry streamRegistry;

    public ChatReplyNode(AgentFactory agentFactory,
            GenerationStreamRegistry streamRegistry) {
        this.chatReplyAgent = agentFactory.createChatReplyAgent();
        this.streamRegistry = streamRegistry;
    }

    public Map<String, Object> execute(DialogState state) {
        String userMessage = state.userMessage().orElse("");
        String dialogId = state.dialogId().orElse("");
        GenerationStreamEmitter emitter = streamRegistry.get(dialogId);

        if (emitter == null) {
            log.warn("[chat_reply] 未找到 dialogId={} 的 emitter，回退非流式", dialogId);
            return Map.of(DialogState.DELEGATE_RESULT, "[闲聊回复失败：SSE 连接未建立]");
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();
        final boolean[] stopped = {false};

        chatReplyAgent.reply(userMessage)
                .onPartialResponse(token -> {
                    if (streamRegistry.isStopRequested(dialogId)) {
                        stopped[0] = true;
                        future.complete(responseBuilder.toString());
                        return;
                    }
                    emitter.emitChatToken(token);
                    responseBuilder.append(token);
                })
                .onCompleteResponse(chatResponse -> {
                    if (!stopped[0]) {
                        future.complete(responseBuilder.toString());
                    }
                })
                .onError(error -> {
                    if (!stopped[0]) {
                        future.completeExceptionally(error);
                    }
                })
                .start();

        try {
            String fullResponse = future.join();
            emitter.emitChatComplete(fullResponse);
            log.info("[chat_reply] 流式回复完成: dialogId={}, length={}", dialogId, fullResponse.length());
            return Map.of(DialogState.DELEGATE_RESULT, fullResponse);
        } catch (Exception e) {
            log.error("[chat_reply] 流式回复失败: dialogId={}", dialogId, e);
            emitter.error("闲聊回复失败: " + e.getMessage());
            return Map.of(DialogState.DELEGATE_RESULT, "[闲聊回复失败]");
        }
    }
}
```

**修改**：`DialogRouter.java` — 构造函数注入 `ChatReplyNode` 签名不变（Spring 自动注入新依赖）。但 `DialogRouterTest.setUp()` 需更新 `ChatReplyNode` 构造（见步骤 8）。

---

## 步骤 5：SseStreamEmitter 提取为独立类

**新建文件**：`workbench/ai/observer/SseStreamEmitter.java`

将 `GenerationService.SseStreamEmitter`（private static 内部类）提取为 public 顶层类，实现 `GenerationStreamEmitter`。字段：`taskId`、`SseEmitter emitter`、`ObjectMapper objectMapper`。所有 send / safeComplete 方法保持原逻辑。

**修改**：`workbench/service/GenerationService.java`
- 删除内部 `SseStreamEmitter` 类
- 所有 `new SseStreamEmitter(taskId, emitter, objectMapper)` 改为引用新的顶层类
- import 新的 `SseStreamEmitter`

**修改**：`SseStreamEmitter` 实现新增的 `emitChatToken` / `emitChatComplete`（供 ChatService 使用）。

---

## 步骤 6：ChatService（对话服务层）

**新建文件**：`workbench/service/ChatService.java`

镜像 `GenerationService` 的线程模型，但驱动 `DialogRouter` 而非 `CodeGenPipeline`。

**依赖注入**：`DialogRouter dialogRouter`、`DialogMapper dialogMapper`、`ChatMessageMapper chatMessageMapper`、`GenerationStreamRegistry streamRegistry`、`ObjectMapper objectMapper`、`@Qualifier(GENERATION_EXECUTOR) Executor executor`、`ProjectService projectService`（可选，验证 projectId）。

**核心方法**：

```java
// 1. 创建会话
public String createDialog(Long projectId, String title) {
    String dialogId = UUID.randomUUID().toString().replace("-", "");
    DialogEntity dialog = new DialogEntity();
    dialog.setDialogId(dialogId);
    dialog.setProjectId(projectId);
    dialog.setTitle(title != null ? title : "新对话");
    dialog.setStatus("active");
    dialogMapper.insert(dialog);
    return dialogId;
}

// 2. 发送消息并打开 SSE 流（合并 send + stream 为一个端点）
public SseEmitter sendMessage(String dialogId, String userMessage) {
    DialogEntity dialog = dialogMapper.selectOne(
            new LambdaQueryWrapper<DialogEntity>().eq(DialogEntity::getDialogId, dialogId));
    if (dialog == null) {
        throw new BusinessException(ResultCode.DIALOG_NOT_FOUND);
    }

    // 落库用户消息
    saveChatMessage(dialog.getProjectId(), dialogId, "user", userMessage);

    // 建立 SSE
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
    SseStreamEmitter sseEmitter = new SseStreamEmitter(dialogId, emitter, objectMapper);
    streamRegistry.register(dialogId, sseEmitter);
    // 心跳 + onCompletion/onTimeout/onError 清理（仿 GenerationService）

    // 异步驱动 DialogRouter
    executor.execute(() -> runDialog(dialogId, dialog.getProjectId(), userMessage, sseEmitter, context));
    return emitter;
}

// 3. 停止回复
public void stopDialog(String dialogId) {
    streamRegistry.requestStop(dialogId);
    stopStreamProcessing(dialogId);
}

// 4. 查询历史消息
public List<ChatMessageEntity> getMessages(String dialogId) { ... }

// 5. 查询会话列表
public List<DialogEntity> listDialogs(Long projectId) { ... }
```

**runDialog** 方法（驱动 DialogRouter）：
```java
private void runDialog(String dialogId, Long projectId, String userMessage,
        SseStreamEmitter emitter, StreamContext context) {
    try {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DialogState.DIALOG_ID, dialogId);
        inputs.put(DialogState.USER_MESSAGE, userMessage);
        if (projectId != null) {
            inputs.put(DialogState.PROJECT_ID, String.valueOf(projectId));
        }

        NodeOutput<DialogState> last = null;
        for (NodeOutput<DialogState> output :
                dialogRouter.getCompiledGraph().stream(inputs)) {
            if (context.stopped) break;
            last = output;
        }
        if (context.stopped) return;

        if (last != null) {
            String reply = last.state().delegateResult().orElse("");
            // 落库助手回复
            saveChatMessage(projectId, dialogId, "assistant", reply);
            // chat_reply 节点已在内部发过 emitChatComplete，
            // 这里只在非 chat 意图时发 complete 事件
            DialogIntent intent = last.state().intent().orElse(DialogIntent.CHAT);
            if (intent != DialogIntent.CHAT) {
                emitter.complete("", 0, 0); // 非 chat 意图：桩节点结果
            }
        }
    } catch (Exception e) {
        emitter.error("对话处理失败: " + e.getMessage());
    } finally {
        emitter.safeComplete();
        cleanup(dialogId);
    }
}
```

> **关键**：chat_reply 节点在 execute 内部已调用 `emitChatToken` / `emitChatComplete`，所以 runDialog 不需要再发 token 事件。但需要在完成后落库 assistant 消息。

---

## 步骤 7：ResultCode 扩展 + DTO

**修改**：`common/exception/ResultCode.java` — 新增：
```java
DIALOG_NOT_FOUND(40404, "会话不存在"),
```

**新建 DTO records**（`common/model/`，仿现有 record 模式，implements Serializable + serialVersionUID）：
1. `CreateDialogRequest.java` — `Long projectId`（可空，闲聊无项目）、`String title`（可空）
2. `SendMessageRequest.java` — `@NotBlank String message`、`@Size(max=2000)`
3. `DialogResponse.java` — `String dialogId`、`Long projectId`、`String title`、`String status`、`LocalDateTime createdAt`
4. `ChatMessageResponse.java` — `Long id`、`String role`、`String content`、`LocalDateTime createdAt`

---

## 步骤 8：ChatController

**新建文件**：`workbench/web/ChatController.java`

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    // POST /api/chat/dialog —— 创建会话，返回 dialogId
    @PostMapping("/dialog")
    public Result<DialogResponse> createDialog(@Valid @RequestBody CreateDialogRequest request) { ... }

    // POST /api/chat/{dialogId}/send —— 发消息 + 打开 SSE 流
    @PostMapping("/{dialogId}/send")
    public SseEmitter sendMessage(@PathVariable String dialogId,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(dialogId, request.message());
    }

    // POST /api/chat/{dialogId}/stop —— 停止回复
    @PostMapping("/{dialogId}/stop")
    public Result<Void> stop(@PathVariable String dialogId) { ... }

    // GET /api/chat/{dialogId}/messages —— 查询历史消息
    @GetMapping("/{dialogId}/messages")
    public Result<List<ChatMessageResponse>> getMessages(@PathVariable String dialogId) { ... }

    // GET /api/chat/dialogs?projectId=xx —— 查询会话列表
    @GetMapping("/dialogs")
    public Result<List<DialogResponse>> listDialogs(@RequestParam(required = false) Long projectId) { ... }
}
```

> **端点设计说明**：清单要求 `POST /api/chat/send` + `GET /api/chat/stream/{dialogId}`。但闲聊是流式回复，发消息即开流，分离 send/stream 会引入"发消息后还要再连 SSE"的竞态。**改为 `POST /api/chat/{dialogId}/send` 直接返回 SseEmitter**（发消息即流），更符合 SSE 流式回复的自然语义。这个偏差会在计划中向用户说明。

---

## 步骤 9：DialogRouterTest 适配

**修改**：`src/test/.../ai/dialog/DialogRouterTest.java`

ChatReplyNode 构造签名变更（新增 AgentFactory + GenerationStreamRegistry）。更新：
1. `setUp()` — `ChatReplyNode chatReplyNode = new ChatReplyNode(agentFactory, streamRegistry)`；需 `@Mock GenerationStreamRegistry streamRegistry`
2. `EndToEndExecution.routerWithAnalyzer()` — 同上更新构造
3. 端到端测试中 chat_reply 桩不再返回占位文本——现在调用真实 `chatReplyAgent.reply()`。**需 mock `AgentFactory.createChatReplyAgent()`** 返回一个返回 `StubTokenStream` 的 mock agent。将 `StubTokenStream` 从 `PipelineNodesTest` 提取为测试工具类，或在 DialogRouterTest 中内联一个简化版。

> **决策**：将 `StubTokenStream` 提取为 `src/test/.../testutil/StubTokenStream.java`（public），PipelineNodesTest 和 DialogRouterTest 共用。

---

## 步骤 10：ChatController 单测（MockMvc）

**新建文件**：`src/test/.../web/ChatControllerTest.java`

`@WebMvcTest(ChatController.class)` + `@MockBean ChatService`。

测试组：
1. `POST /api/chat/dialog` — 创建会话成功 → 200 + dialogId
2. `POST /api/chat/dialog` — 缺 title → 仍 200（title 可空）
3. `POST /api/chat/{dialogId}/send` — message 为空 → 400 校验失败
4. `POST /api/chat/{dialogId}/stop` — 成功 → 200
5. `GET /api/chat/{dialogId}/messages` — 返回消息列表
6. `GET /api/chat/dialogs` — 按 projectId 过滤

不测 SSE 实际流（MockMvc 不适合测 SSE），只测 REST 契约。

---

## 步骤 11：集成测试（DialogRouter → ChatReplyNode → SSE 端到端）

**新建文件**：`src/test/.../ai/dialog/ChatFlowIntegrationTest.java`

`@SpringBootTest` + `@AutoConfigureMockMvc`，用 NoOpStreamingModel 降级跑（无 API Key）。

测试：
1. **NoOp 降级场景**：ChatReplyNode 调用 NoOpStreamingModel → `chat()` 抛 IllegalStateException → onError 回调 → emitter.error() → DELEGATE_RESULT = "[闲聊回复失败]"。验证图不崩溃、错误正确传播。
2. **Mock 模型场景**：用 `@MockBean AgentFactory` override `createChatReplyAgent()` 返回返回 `StubTokenStream("你好！我是灵码工坊助手。")` 的 mock agent。创建会话 → 发消息 → 验证 SSE 事件序列含 `message` 事件（chat_reply token）→ complete。

---

## 步骤 12：提取 StubTokenStream 测试工具类

**新建文件**：`src/test/.../testutil/StubTokenStream.java`

将 `PipelineNodesTest.StubTokenStream`（private static）提取为 public 顶层类，原 PipelineNodesTest 改为引用新类。

---

## 步骤 13：更新开发清单文档

**修改**：`项目文档/迭代文档/4.灵码工坊-智能体协同开发清单.md`
- §4 Phase 2 的 4-1 ~ 4-6 逐项勾选，补完成日期与产出文件
- §9 关键文件索引追加 `workbench/ai/dialog/` + `workbench/web/ChatController.java`
- §10 变更日志追加一条
- §1 阶段进度总览表 Phase 2 状态改为 ✅

---

## 新增/修改文件清单

**新建（11 个）**：
1. `workbench/ai/service/ChatReplyAgent.java`
2. `workbench/ai/observer/SseStreamEmitter.java`（从 GenerationService 提取）
3. `workbench/service/ChatService.java`
4. `workbench/web/ChatController.java`
5. `common/model/CreateDialogRequest.java`
6. `common/model/SendMessageRequest.java`
7. `common/model/DialogResponse.java`
8. `common/model/ChatMessageResponse.java`
9. `resources/prompts/chat-reply-system.txt`
10. `src/test/.../testutil/StubTokenStream.java`
11. `src/test/.../web/ChatControllerTest.java`
12. `src/test/.../ai/dialog/ChatFlowIntegrationTest.java`

**修改（8 个）**：
1. `workbench/ai/factory/AgentType.java`（+CHAT_REPLY）
2. `workbench/ai/factory/AgentFactory.java`（+createChatReplyAgent）
3. `workbench/ai/observer/GenerationStreamEmitter.java`（+emitChatToken / emitChatComplete）
4. `workbench/ai/dialog/ChatReplyNode.java`（桩 → 流式真实逻辑）
5. `workbench/service/GenerationService.java`（删除内部 SseStreamEmitter，改引用顶层类）
6. `common/exception/ResultCode.java`（+DIALOG_NOT_FOUND）
7. `application-dev.yml`（+chat-reply agent 配置）
8. `src/test/.../ai/dialog/DialogRouterTest.java`（适配 ChatReplyNode 新构造）
9. `src/test/.../ai/pipeline/PipelineNodesTest.java`（StubTokenStream 提取后改引用）
10. `4.灵码工坊-智能体协同开发清单.md`（状态回写）

---

## 验证方式
1. `mvnw test -pl lingmaForge-backend` 全绿（含新增 ChatControllerTest + ChatFlowIntegrationTest + 适配后的 DialogRouterTest）
2. 现有 140 例不回归（Phase 0 的 96 + Phase 1 的 12 + 其余）
3. 集成测试验证 NoOp 降级路径不崩溃
4. 日志中能看到 DialogRouter 图跑通 intent_detection → chat_reply → END，SSE message 事件逐 token 推送

## 不做的事（留给后续 Phase）
- ❌ 不桥接 CodeGenPipeline（Phase 4，DelegateCodegenNode 仍是桩）
- ❌ 不桥接 IterationAgent（Phase 3，DelegateIterateNode 仍是桩）
- ❌ 不动前端（Phase 5）
- ❌ 不做多轮上下文（MESSAGES channel 预留，但 Phase 2 只传当前轮 USER_MESSAGE）
- ❌ 不做会话标题自动生成（title 由前端传或默认"新对话"）

## 对清单 §4 的两处偏差（需用户确认）

1. **端点合并**：清单写 `POST /api/chat/send` + `GET /api/chat/stream/{dialogId}` 两个端点；计划改为 `POST /api/chat/{dialogId}/send` 一个端点直接返回 SseEmitter。理由：流式回复中"发消息"和"开流"分离会引入竞态（消息已发但 SSE 未连上时 token 丢失）。
2. **新增 createDialog / messages / dialogs 端点**：清单只列了 send/stream/stop，但对话持久化（4-3）需要创建会话和查询历史的端点支撑，故补充。