# 灵码工坊 (LingmaForge) 后端架构文档

> 生成时间：2026-07  
> 适用范围：`lingmaForge-backend` 代码生成平台的完整技术架构，涵盖首次生成流水线、对话式迭代流水线、对话记忆系统、流式输出基础底座。

---

## 1. 整体架构概览

灵码工坊后端采用 **Spring Boot + LangChain4j + LangGraph4j** 的**混合架构**——workflow（StateGraph 固定编排）负责确定性步骤（构建验证、预览部署），agent（AiServices + TokenStream 工具循环）负责需要模型自主决策的核心阶段（代码生成、迭代编辑）。

<pre class="mermaid">
flowchart TB
    subgraph Frontend["前端 (Vue 3 + Pinia)"]
        EC[EventSource SSE 连接]
        RC[REST API 调用]
    end

    subgraph Controllers["Controller 层"]
        GC[GenerationController<br/>/api/generation/*<br/>/api/stream/*]
        PC[ProjectController<br/>/api/projects/*]
        SC[SandboxController]
    end

    subgraph Services["Service 层"]
        GS[GenerationService<br/>任务编排 + Flux SSE 管理]
        SS[SandboxService<br/>npm install/build]
        PFS[ProjectFileService<br/>磁盘+数据库双写]
    end

    subgraph Pipelines["Pipeline 层 (LangGraph4j StateGraph)"]
        GP[CodeGenPipeline<br/>6 节点：首次生成]
        IP[IterationPipeline<br/>4 节点：对话式迭代]
    end

    subgraph Nodes["Node 层 (11 个节点)"]
        RA[RequirementAnalysis]
        EP[ExecutionPlanning]
        CG[CodeGeneration]
        SO[StyleOptimization]
        BV[BuildVerification]
        PD[PreviewDeploy]
        CP[CodePatch<br/>agentic 迭代编辑]
    end

    subgraph Agents["Agent 层 (AiServices)"]
        CGA[CodeGenAgent<br/>流式代码生成]
        IE[IterationEditor<br/>agentic 流式编辑+工具循环]
        IA[IterationAgent<br/>结构化意图/规划分析]
    end

    subgraph Tools["工具层 (@Tool)"]
        FT[FileTools<br/>writeFile/patchFile/validateCode]
        PCT[ProjectContextTools<br/>readFileContext/readProjectContext]
        UPT[UpdatePlanTool<br/>计划追踪]
        ET[ExitTool<br/>终止信号]
    end

    subgraph Stream["流式底座"]
        SB[StreamingBridge<br/>TokenStream 自动桥接]
        FSE[FluxStreamEmitter<br/>事件→SSE Flux]
        SE[StreamEvent<br/>统一5事件封装]
    end

    subgraph Memory["记忆系统"]
        IMS[InMemoryChatMemoryStore]
        CCS[CompactingChatMemoryStore]
        CCPS[ContextCompactionService]
        MWCM[MessageWindowChatMemory<br/>160 窗口]
    end

    subgraph DB["持久层"]
        H2[(H2 Database<br/>4 张表)]
        FS[Workspace 磁盘<br/>项目文件落盘]
    end

    EC -->|SSE Flux| GC
    RC -->|REST| GC
    RC -->|REST| PC
    GC --> GS
    GS -->|streamGeneration| GP
    GS -->|streamIteration| IP
    GP --> RA --> EP --> CG --> SO --> BV --> PD
    IP --> CP --> BV --> PD
    CG -->|StreamingBridge| SB
    CP -->|StreamingBridge| SB
    SB --> FSE --> EC
    CG --> CGA
    CP --> IE
    CGA --> Memory
    IE --> Memory
    IE --> FT
    IE --> PCT
    IE --> UPT
    IE --> ET
    Memory --> IMS --> CCS --> MWCM
    CCS --> CCPS
    FT --> PFS
    PFS --> H2
    PFS --> FS
</pre>

### 工程包结构

| 包 | 职责 |
|---|---|
| `workbench.ai.pipeline` | CodeGenPipeline（6 节点首次生成）、IterationPipeline（4 节点对话式迭代）、CodeGenState（共享状态黑板） |
| `workbench.ai.node` | 11 个节点类——每个是 LangGraph4j StateGraph 的一个顶点，实现 `AbstractCodeGenNode` 基类 |
| `workbench.ai.service` | 6 个 AiServices 接口契约——LangChain4j 通过动态代理把接口方法映射为 LLM 调用 |
| `workbench.ai.factory` | AgentFactory——按 `AgentType` 枚举解析配置的模型别名，创建带 system prompt 和 @Tool 的 agent 实例 |
| `workbench.ai.tool` | 4 个 @Tool 类——给 agent 提供文件写入、上下文读取、计划追踪、终止信号能力 |
| `workbench.ai.stream` | 流式底座——StreamingBridge（TokenStream 桥接）、FluxStreamEmitter（事件→SSE）、StreamEvent（统一封装） |
| `workbench.ai.memory` | 记忆系统——InMemoryChatMemoryStore、CompactingChatMemoryStore、ContextCompactionService |
| `workbench.ai.plan` | PlanTracker + PlanItem——agentic 迭代的 DAG 计划纪律 |
| `workbench.ai.observer` | GenerationContext（ThreadLocal）、GenerationStreamEmitter（发射器接口）、GenerationStreamRegistry（按 taskId 注册） |
| `workbench.ai.support` | TemplateFilePolicy——受保护的 Vue 模板文件列表 |
| `workbench.service` | 6 个业务 service：GenerationService（核心编排）、SandboxService（npm 构建）、ProjectService 等 |
| `workbench.web` | GenerationController（6 个 REST/SSE 端点）、ProjectController、SandboxController |
| `infra.config` | 8 个 @Configuration 类——LangChain4j 多模型、ChatMemory 装饰链、CORS、MyBatis-Plus、异步线程池 |
| `infra.health` | HealthController——`/api/health` 就绪探针 |
| `common.model` | 25 个 DTO record——pipeline 状态载体（RequirementSpec、PlanResult、ModificationPlan 等）、请求/响应模型 |
| `common.exception` | BusinessException、GlobalExceptionHandler、ResultCode 枚举 |
| `common.api` | `Result<T>` 统一响应信封 |

---

## 2. 数据库与持久化 (H2)

系统使用 **H2 内嵌文件数据库**（`./data/lingmaforge`），开发环境开箱即用，生产环境可切换 MySQL。`schema.sql` 在启动时自动执行（`spring.sql.init.mode=always`）。

<pre class="mermaid">
erDiagram
    lf_project ||--o{ lf_project_file : "1:N"
    lf_project ||--o{ lf_generation_task : "1:N"
    lf_project ||--o{ lf_chat_message : "1:N"

    lf_project {
        bigint id PK "Snowflake"
        varchar name
        varchar description
        varchar framework "默认 vue-vite-ts"
        varchar status "draft/running/completed"
        varchar sandbox_url
        timestamp created_at
        timestamp updated_at
    }

    lf_project_file {
        bigint id PK "Snowflake"
        bigint project_id FK
        varchar path "唯一约束 (project_id, path)"
        varchar file_type
        varchar status "new/modified/unchanged"
        clob content
        varchar checksum
        timestamp created_at
        timestamp updated_at
    }

    lf_generation_task {
        bigint id PK "Snowflake"
        varchar task_id UK "UUID = SSE streamId = StateGraph threadId"
        bigint project_id FK
        varchar task_type "create / iterate"
        clob prompt
        varchar current_stage
        varchar status "running/completed/failed/stopped"
        int build_time
        varchar preview_url
        clob error_message
        timestamp created_at
        timestamp updated_at
    }

    lf_chat_message {
        bigint id PK "Snowflake"
        bigint project_id FK
        varchar task_id
        varchar role "user / assistant"
        clob content
        timestamp created_at
    }
</pre>

### 文件双写机制

生成的每一个文件同时写入两处：
- **数据库**（`lf_project_file`）：供前端文件树、代码编辑器实时读取
- **物理磁盘**（`./workspace/{projectId}/`）：供 `npm install && npm run build` 使用

`ProjectFileServiceImpl` 保证每次写入都同步更新两处，并为每个文件计算 checksum 用于幂等去抖。

---

## 3. 首次生成流水线（用户说"帮我做一个 xxx 应用"）

当用户在 frontend 输入需求并提交，系统通过 **6 节点 LangGraph4j StateGraph** 完成从需求到可运行项目的全流程。所有节点共享同一个 `CodeGenState` 黑板通过 channel 传递中间结果。

<pre class="mermaid">
sequenceDiagram
    participant Browser as 浏览器 (EventSource)
    participant GC as GenerationController
    participant GS as GenerationService
    participant GP as CodeGenPipeline
    participant Nodes as 6 个节点
    participant Agent as CodeGenAgent
    participant SB as StreamingBridge
    participant FSE as FluxStreamEmitter
    participant DB as H2 / 文件系统

    Browser->>GC: POST /api/generation/create
    GC->>GS: createGeneration(projectId, prompt)
    GS->>DB: 写入 GenerationTask (status=running)
    GS-->>GC: taskId
    GC-->>Browser: {taskId}

    Browser->>GC: GET /api/stream/generation/{taskId} (SSE)
    GC->>GS: streamGeneration(taskId)
    GS->>GS: Flux.create(sink) → 创建 FluxStreamEmitter
    GS->>GS: executor.execute → 异步启动流水线

    Note over GS,GP: === 流水线执行 ===

    GS->>GP: graph.stream(inputs={prompt, projectId, taskId})

    loop 6 节点顺序执行
        Nodes->>Nodes: setupContext() → 从 Registry 取 emitter → 注 ThreadLocal
        Nodes->>FSE: emitNodeStart("requirement_analysis", "正在分析需求...")
        Nodes->>Agent: LLM 调用 (结构化输出)
        Agent-->>Nodes: 结构化结果 (RequirementSpec / PlanResult 等)
        Nodes->>FSE: emitNode("需求分析完成：应用 XXX")
        Nodes->>FSE: emitNodeEnd("requirement_analysis")
        Nodes->>DB: 写入 CodeGenState channel
    end

    Note over Nodes,Agent: === 代码生成节点（核心、唯一真流式） ===

    Nodes->>Agent: agent.generate(memoryId, prompt) → TokenStream
    Nodes->>SB: streamingBridge.bridge(tokenStream, ctx)

    loop 每个文件的每个 token
        Agent-->>SB: onPartialResponse(token)
        SB->>FSE: emitFileToken(path, token) → SSE "token" event
        Agent-->>SB: onPartialThinking(thinking)
        SB->>FSE: emitThinking(nodeName, token) → SSE "thinking" event
    end

    Agent-->>SB: onCompleteResponse
    SB->>Nodes: ctx.onComplete() → resolve CompletableFuture
    Nodes->>DB: cleanupCodeOutput → writeFile(disk+DB)
    Nodes->>FSE: emitFileComplete(path) → SSE "token" event

    Note over GP: === 构建验证 ===
    Nodes->>Nodes: npm install && npm run build
    alt 构建成功
        Nodes->>FSE: emitLog("构建成功（45s）")
        GP->>GP: 路由 → preview_deploy
        Nodes->>FSE: complete(url, port, buildTime) → SSE "done" event
        GS->>GS: taskService.markCompleted()
    else 构建失败
        Nodes->>FSE: emitLog("构建失败: ...")
        alt retryCount ≤ 2
            GP->>GP: 回退 → code_generation (重新生成报错文件)
        else retryCount > 2
            GP->>GP: 路由 → error_end
            Nodes->>FSE: error("生成失败: ...") → SSE "error" event
        end
    end

    GS->>GS: GenerationContext.clear() + sink.complete()
    GS-->>Browser: SSE 流关闭
</pre>

### CodeGenPipeline 状态图

<pre class="mermaid">
stateDiagram-v2
    [*] --> requirement_analysis
    requirement_analysis --> execution_planning
    execution_planning --> code_generation
    code_generation --> style_optimization
    style_optimization --> build_verification

    build_verification --> preview_deploy : 构建成功
    build_verification --> code_generation : 失败 (retryCount ≤ 2)
    build_verification --> error_end : 失败 (retryCount > 2)

    preview_deploy --> [*]
    error_end --> [*]

    note right of code_generation
        多文件并行流式生成
        CompletableFuture 线程池
        每个文件独立 TokenStream
    end note

    note right of build_verification
        npm install + npm run build
        ProcessBuilder 子进程
        超时 180s
    end note
</pre>

### 多文件并行生成机制

`CodeGenerationNode` 是唯一带**真 token 级流式**的节点。它从 `PlanResult.files()` 中取文件列表，过滤掉受保护的模板文件（`package.json`、`vite.config.ts` 等），对每个 `FilePlan` 创建一个 `CompletableFuture.runAsync()`：

```
CompletableFuture.allOf(futures).get(300s)
  ├─ Future 1: generateOneFile("src/App.vue")  ── 独立线程
  ├─ Future 2: generateOneFile("src/main.ts")   ── 独立线程
  ├─ Future 3: generateOneFile("src/store.ts")  ── 独立线程
  └─ ...
```

每个 `generateOneFile` 通过 `StreamingBridge` 驱动一个 `CodeGenAgent.generate(memoryId, prompt)` 的 TokenStream，逐 token 向后端推送 SSE `token{kind:file_token}` 事件。流式结束后 Java 侧做 `cleanupCodeOutput()` 清洗（去 Markdown 包裹、客套话），然后 `projectFileService.writeFile()` 双写落盘。

### 节点详解

| 节点 | NODE_NAME | LLM 调用方式 | 产出 |
|---|---|---|---|
| `RequirementAnalysisNode` | `requirement_analysis` | 同步 ChatModel → 结构化 `RequirementSpec` | `CodeGenState.ANALYSIS_RESULT` |
| `ExecutionPlanningNode` | `execution_planning` | 同步 ChatModel → 结构化 `PlanResult` | `CodeGenState.PLAN_RESULT` |
| `CodeGenerationNode` | `code_generation` | **流式 StreamingChatModel** TokenStream | `CodeGenState.GENERATED_FILES` |
| `StyleOptimizationNode` | `style_optimization` | `AiServices` agent-loop + FileTools | 直接写文件（patchFile/writeFile） |
| `BuildVerificationNode` | `build_verification` | 无 LLM——`ProcessBuilder` npm | `CodeGenState.BUILD_STATUS` |
| `PreviewDeployNode` | `preview_deploy` | 无 LLM——sandbox URL | `CodeGenState.PREVIEW_URL` |

---

## 4. 对话式迭代流水线（用户说"把标题改成蓝色"）

迭代修改采用**4 节点 agentic pipeline**——比首次生成的 6 节点更精简，核心变化是 `CodePatchNode` 内联了原 `IterationIntentAnalysisNode` + `ProjectContextLoadNode` + `ModificationPlanningNode` 的三节点逻辑，本身就是完整的 agent 循环。

<pre class="mermaid">
sequenceDiagram
    participant Browser as 浏览器
    participant GC as GenerationController
    participant GS as GenerationService
    participant IP as IterationPipeline
    participant CP as CodePatchNode
    participant IE as IterationEditor
    participant Tools as 工具层
    participant Plan as PlanTracker
    participant Mem as 记忆系统
    participant BV as BuildVerification

    Browser->>GC: POST /api/generation/iterate
    GC->>GS: iterate(projectId, "把标题改成蓝色")
    GS->>DB: 写入 GenerationTask (type=iterate)

    Browser->>GC: GET /api/stream/iteration/{taskId} (SSE)
    GC->>GS: streamIteration(taskId)
    GS->>GS: Flux.create → 创建 FluxStreamEmitter → 异步启动

    Note over CP,Mem: === CodePatchNode (单一 agentic 节点) ===

    CP->>CP: buildProjectContext() → 读取文件结构+内容
    CP->>Mem: compactionService.autoCompactIfNeeded()
    CP->>CP: buildEditPrompt(用户指令 + 上下文 + 构建错误)
    CP->>IE: editor.edit(memoryId=projectId, prompt) → TokenStream

    loop Agent 工具循环 (推理 + 工具调用)
        IE-->>CP: onPartialThinking(thinking)
        CP->>Browser: SSE "thinking" event
        IE->>Tools: @Tool 调用
        Tools->>Tools: readProjectContext / readFileContext
        Tools->>Tools: writeFile / patchFile
        Tools->>Plan: onToolExecuted() → nagCount++
        Plan-->>Tools: Nag 提醒 (≥3 次非 updatePlan 调用了)
        Tools->>Plan: updatePlan(items) → DAG 校验 → 返回进度
        Tools-->>CP: SSE "tool_call" event (调用中/完成)
    end

    IE->>IE: exit() → 工具循环终止
    CP->>Plan: planTracker.clear()

    CP->>BV: → build_verification
    BV->>BV: npm install && npm run build
    alt 构建成功
        BV->>BV: → preview_deploy → END
    else 失败 (retryCount ≤ 2)
        BV->>CP: 回退 → CodePatchNode (BUILD_ERROR 注入 prompt)
    else 失败 (retryCount > 2)
        BV->>BV: → iteration_error_end
    end
</pre>

### IterationPipeline 状态图

<pre class="mermaid">
stateDiagram-v2
    [*] --> code_patch
    code_patch --> build_verification

    build_verification --> preview_deploy : 成功
    build_verification --> code_patch : 失败 (retryCount ≤ 2)
    build_verification --> iteration_error_end : 失败 (retryCount > 2)

    preview_deploy --> [*]
    iteration_error_end --> [*]

    note right of code_patch
        单一 agentic 节点
        内联上下文装载 + 指令理解
        IterationEditor TokenStream
        工具集：writeFile/patchFile
                readFileContext/readProjectContext
                updatePlan/exit
    end note

    note left of build_verification
        与首次生成共享同一节点
        npm install + npm run build
    end note
</pre>

### Agentic 工具循环

模型在 `IterationEditor.edit()` 中拥有全部 7 个工具（`writeFile`/`patchFile`/`validateCode`/`readFileContext`/`readProjectContext`/`updatePlan`/`exit`），执行典型的"理解 → 读 → 改 → 提交计划 → 退出"循环：

```
1. readProjectContext()      → 了解项目结构
2. readFileContext([paths])  → 读取要修改的文件当前内容
3. writeFile(path, content)  → 写入修改后的文件
4. updatePlan([items])       → 更新计划进度（触发 DAG 校验）
5. ... 重复 2-4 直到完成 ...
6. exit()                    → 显式终止 agent 循环
```

**防跑偏双机制**：

| 机制 | 触发条件 | 效果 |
|---|---|---|
| **Nag 提醒** | 连续 ≥3 次非 updatePlan 工具调用 | FileTools 在工具返回末尾注入 `<reminder>请调用 updatePlan 更新计划...</reminder>` |
| **exit 终止** | 模型自主调用 | 循环退出，避免回文浪费 token |

---

## 5. 对话记忆系统

记忆系统确保**多轮对话上下文连续**——用户在第二轮说"把刚才改的标题再换成绿色"，系统能知道"刚才改的"是指哪个文件、哪行代码。

<pre class="mermaid">
flowchart TB
    subgraph 触发层["每次调 Agent 前"]
        CP[CodePatchNode]
        CG[CodeGenerationNode]
    end

    subgraph Layer3["Layer 3: 自动摘要压缩 (ContextCompactionService)"]
        EST[估算 token: chars / 3]
        THR[阈值: 800k (可配)]
        LLM[调 deepseek-flash<br/>生成摘要]
        SUM["摘要含 A→B 变更对照<br/>[对话已压缩...]"]
    end

    subgraph Layer2["Layer 2: 微压缩装饰器 (CompactingChatMemoryStore)"]
        KEEP[保留最近 8 条工具结果原文]
        COMP[压缩旧工具结果 =&gt;<br/>[已执行: writeFile]]
    end

    subgraph Layer1["Layer 1: 内存存储 (InMemoryChatMemoryStore)"]
        KV["ConcurrentHashMap&lt;String, List&lt;ChatMessage&gt;&gt;<br/>key = projectId + '_vue-project'"]
    end

    subgraph Window["滑动窗口 (MessageWindowChatMemory)"]
        WIN[maxMessages = 160]
    end

    CP --> EST
    CG --> EST
    EST --> THR
    THR -->|超阈值| LLM
    LLM --> SUM
    SUM --> WIN
    THR -->|未超| COMP
    WIN --> COMP
    COMP --> KEEP
    KEEP --> KV
    COMP --> COMP
    COMP --> KV
    KV --> WIN
</pre>

### 三层记忆架构

| 层 | 组件 | 职责 |
|---|---|---|
| **Layer 3** | `ContextCompactionService` | 每次调 agent 前估算 token 量（`totalChars / 3`）。当超过阈值（800k，约 DeepSeek V4-Pro 1M 上下文的 80%）时，调轻量模型生成对话摘要，用一条 `[对话已压缩，完整记录...]` 消息替换全部历史 |
| **Layer 2** | `CompactingChatMemoryStore` | `ChatMemoryStore` 的装饰器：保留最近 8 条 `ToolExecutionResultMessage` 的原始内容，更早的工具结果（>300 字符）替换为 `[已执行：toolName]` 占位符 |
| **Layer 1** | `InMemoryChatMemoryStore` | 基于 `ConcurrentHashMap<String, List<ChatMessage>>` 的内存实现，keyed by `"projectId_vue-project"` |
| **滑动窗口** | `MessageWindowChatMemory` | LangChain4j 内置：最多保留 160 条消息，超过则 FIFO 淘汰 |

### 记忆的接入方式

```
AgentFactory.createIterationEditor()
  .chatMemoryProvider(id → chatMemoryProvider.apply(id + "_vue-project"))

AgentFactory.createCodeGenAgent()
  .chatMemoryProvider(id → chatMemoryProvider.apply(id + "_vue-project"))

@MemoryId long memoryId    ← CodeGenAgent.generate(@MemoryId long memoryId, ...)
@MemoryId long memoryId    ← IterationEditor.edit(@MemoryId long memoryId, ...)
                              ↑ 传入 projectId，每个项目独立记忆
```

### 记忆生命周期

- **创建**：首次调 agent 时，`chatMemoryProvider` 为新 `memoryId` 创建空的 `MessageWindowChatMemory`
- **更新**：每次 `agent.generate()` 或 `editor.edit()` 后，LangChain4j 自动把本轮对话追加入记忆
- **压缩**：调用前触发 `compactionService.autoCompactIfNeeded()`
- **清理**：`ProjectServiceImpl.deleteProject()` 调用 `memoryStore.deleteByPrefix(projectId + "_")`

---

## 6. 流式输出基础底座

流式底座采用 **4 层架构**，把 LangChain4j 的 `TokenStream` 经过统一的协议封装、事件转换、SSE 传输，最终推送到浏览器。

<pre class="mermaid">
flowchart TB
    subgraph L1["Layer 1: LangChain4j TokenStream"]
        TS["CodeGenAgent.generate() → TokenStream<br/>IterationEditor.edit() → TokenStream"]
        CB["回调：onPartialResponse / onPartialThinking<br/>onCompleteResponse / onError"]
    end

    subgraph L2["Layer 2: StreamingBridge + StreamingContext"]
        SC["StreamingContext<br/>• emitter • nodeName • taskId<br/>• stopRegistry • onToken/onComplete/onStop"]
        BR["StreamingBridge.bridge(tokenStream, ctx)"]
        STOP["停止信号检测<br/>每收到 token 检查 isStopRequested()"]
    end

    subgraph L3["Layer 3: FluxStreamEmitter + StreamEvent"]
        FE["FluxStreamEmitter<br/>implements GenerationStreamEmitter"]
        EV["StreamEvent 五事件统一封装<br/>token / thinking / tool_call / done / error"]
        MAP["语义映射<br/>emitNode → token{kind:node_text}<br/>emitFileToken → token{kind:file_token}<br/>emitThinking → thinking<br/>complete → done<br/>error → error"]
    end

    subgraph L4["Layer 4: Spring WebFlux SSE"]
        FL["Flux&lt;ServerSentEvent&lt;String&gt;&gt;"]
        WR["ReactiveTypeHandler<br/>Servlet 异步适配"]
        SSE["text/event-stream<br/>逐条立即 flush"]
    end

    TS --> BR
    SC --> BR
    BR --> STOP
    BR --> FE
    FE --> EV
    EV --> MAP
    FE --> FL
    FL --> WR
    WR --> SSE
    SSE --> Browser[浏览器 EventSource]
</pre>

### 四层详解

**Layer 1 - TokenStream**：LangChain4j 从 AiServices 动态代理返回的流式响应体。`onPartialResponse` 每收到一个文本 token 触发一次；`onPartialThinking` 每收到一个 reasoning token 触发一次；`onCompleteResponse` 流结束时触发。

**Layer 2 - StreamingBridge**：节点不再手写四件套回调。只需创建 `StreamingContext` 并调用 `streamingBridge.bridge(tokenStream, ctx)`：

```java
// CodePatchNode 中的实际代码
StreamingContext ctx = StreamingContext.builder()
    .emitter(emitter).nodeName(NODE_NAME).taskId(taskId)
    .stopRegistry(getStreamRegistry())
    .onToken(t -> emitter.emitNode(NODE_NAME, t, "TEXT"))
    .onComplete(() -> emitter.emitNode(NODE_NAME, "完成", "TEXT"))
    .build();
streamingBridge.bridge(editor.edit(projectId, fullPrompt), ctx);
```

Bridge 内部统一完成：
- `onPartialResponse` → 停止信号检测 → 调 `ctx.onToken()` 发射
- `onPartialThinking` → 停止信号检测 → 调 `emitter.emitThinking()`
- `onCompleteResponse` → 调 `ctx.onComplete()`
- `onError` → 调 `emitter.error()`

**Layer 3 - FluxStreamEmitter + StreamEvent 统一封装**：所有高层语义事件收敛为五种 SSE 事件类型：

| SSE `event:` | `data` 载荷 | 触发方法 |
|---|---|---|
| `token` | `{kind, nodeName, text, path, token, content, status}` | `emitNode`/`emitFileToken`/`emitFileComplete`/`emitFile`/`emitLog`/`emitNodeStart`/`emitNodeEnd` |
| `thinking` | `{nodeName, token}` | `emitThinking` |
| `tool_call` | `{id, name, arguments, result}` | `emitToolCall` (result=null 为调用中，非 null 为完成) |
| `done` | `{url, port, buildTime}` | `complete`（AtomicBoolean 幂等，只发一次） |
| `error` | `{nodeName, message}` | `error` |

**Layer 4 - Spring WebFlux SSE**：Controller 返回 `Flux<ServerSentEvent<String>>` + `produces=text/event-stream`。在 Servlet (spring-boot-starter-web + spring-boot-starter-webflux 共存) 模式下，Spring 的 `ReactiveTypeHandler` 把 Flux 适配为 Servlet 异步流式响应，每条 ServerSentEvent 立即 flush。

### 停止信号流

```
用户点 Stop → POST /api/generation/{taskId}/stop
  → streamRegistry.requestStop(taskId)      ← 注册停止标记
  → stopStreamProcessing(taskId)             ← StreamContext.stopped = true

pipeline 循环:
  for (NodeOutput output : graph.stream(inputs)) {
    if (context.stopped) break;               ← 退出图迭代
  }

StreamingBridge 内部:
  onPartialResponse(token → {
    if (stopRegistry.isStopRequested(taskId)) {
      ctx.onStop().accept(accumulatedText);   ← 触发 CompletableFuture resolve
      return;                                  ← 不再发射后续事件
    }
  })

CodeGenerationNode:
  if (streamRegistry.isStopRequested(taskId)) return;  ← 跳过落盘
```

### 推理回放 (Reasoning Replay)

LLM 在工具调用过程中产生的 reasoning_content（思考链）通过 `returnThinking(true)` 和 `sendThinking(true, "reasoning_content")` 进行回放。这是代码生成质量的关键杠杆——多轮工具调用时，模型能记住"为什么上一次选了那个方案"，而不会因为中间的工具返回结果丢失思维线索。

配置在 `AgentFactory.buildStreamingModel()` 中，对 OpenAI 兼容协议（DeepSeek）自动开启：

```java
builder.returnThinking(returnThinking);
builder.sendThinking(sendThinking, thinkingField);  // thinkingField = "reasoning_content"
builder.customHeaders(Map.of("Accept-Encoding", "identity"));  // 禁用 HTTP 压缩防代理截断
```

---

## 7. 多模型路由

系统支持 5 个 AI 模型，通过 `lingma.agents.{agentType}.model` 配置实现**"便宜模型做分析、贵模型写代码"**的分级成本控制。

```
lingma:
  models:
    deepseek-flash:    ← 最便宜 (¥0.001/1K input)
    deepseek-pro:      ← 强推理 (¥0.003/1K input)
    qwen-coder:        ← 代码备份
    qwen-plus:         ← 通用备份
    kimi-code:         ← 编程特化可选升级

  agents:
    requirement-analysis:    → model: deepseek-flash
    execution-planning:      → model: deepseek-flash
    code-generation:         → model: deepseek-pro    ← 最贵模型，只用于写代码
    style-optimization:      → model: deepseek-flash
    iteration-modification:  → model: deepseek-pro    ← 迭代编辑也用强推理
```

如果某个模型的 `api-key` 未设置（环境变量为空），`LangChain4jConfig` 自动跳过该模型的 Bean 创建，`AgentFactory.resolveModel()` 会自动降级到第一个可用模型。

---

## 8. 前端 SSE 协议适配

前端使用 `Pinia` store（`useWorkbenchStore`）管理状态，通过浏览器原生 `EventSource` 连接后端 SSE。

### 事件分发

`attachUnifiedSseListeners()` 函数注册了五个事件监听器：

| `event:` | 前端处理 |
|---|---|
| `token` | 根据 `data.kind` 分发：`node_start`→标记 checklist running、`node_end`→标记 done+flush buffer、`node_text`→打字机动画、`file_token`→累积到 fileTokenBuffer 每 100ms 批量刷新、`file_complete`→落盘日志、`file`→插入/更新文件树、`log`→自动分类日志级别/来源 |
| `thinking` | 累积到 `thinkingBuffer[nodeName]`，每 100ms 刷新到 checklist 面板 |
| `tool_call` | 记录到活动日志："调用工具 {name}…" / "工具 {name} 完成" |
| `done` | 调 `reduceGenerationComplete()` → 关闭 EventSource → 刷新沙箱状态 |
| `error` | 调 `reduceGenerationError()` → 关闭 EventSource |

### 可见节点

| 模式 | 管道节点列表 |
|---|---|
| **首次生成** | `requirement_analysis → execution_planning → code_generation → style_optimization → build_verification → preview_deploy` |
| **对话式迭代** | `code_patch → build_verification → preview_deploy` |

前端 `checklistItems` 计算属性只显示当前管道模式下实际执行的节点，旧版迭代的 7 节点已被收敛到 3 节点。

---

## 9. 技术栈

| 层 | 技术 |
|---|---|
| 框架 | Spring Boot 3.5.7, Java 21 |
| AI 编排 | LangChain4j 1.17.2, LangGraph4j 1.8.19 |
| LLM 供应商 | OpenAI 兼容协议 (DeepSeek / 通义千问 / Moonshot), Anthropic |
| 数据库 | H2 (file, MySQL 兼容模式), MyBatis-Plus 3.5.15 |
| 响应式流 | Project Reactor (spring-boot-starter-webflux), Servlet 异步适配 |
| 前端 | Vue 3 (Composition API), TypeScript 6, Vite 8, Pinia 3 |
| 构建验证 | ProcessBuilder → npm install && npm run build (Node.js 22+) |
| 构建工具 | Maven 3.9+, mvnw wrapper |
