# 灵码工坊 · AI 框架技术选型深度对比

> **文档版本**: v1.0 | **日期**: 2026-06-26
> **背景**: 在方案设计中被提示 `Spring AI Alibaba` 和 `LangGraph4j` 也是值得关注的选择。本文基于 Context7 实时文档查询 + 多个权威对比来源，对四个框架进行全面评估。
> **结论**: 推荐 **Spring AI Alibaba 统一方案**（AI 接入 + AI 编排全部在一个框架内完成）。

---

## 一、候选方案概览

| 方案 | AI 接入层 | AI 编排层 | 依赖数量 |
|------|----------|----------|---------|
| **方案 A (推荐)** | Spring AI Alibaba | Spring AI Alibaba Graph + ReactAgent | **1 个全家桶** |
| 方案 B | Spring AI Alibaba | LangGraph4j | 2 个框架 |
| 方案 C | Spring AI Alibaba | LangChain4j Agent | 2 个框架 |
| 方案 D | Spring AI (vanilla) | LangGraph4j | 2 个框架 |

---

## 二、各框架深度剖析

### 2.1 Spring AI Alibaba（1.1 版本）

**定位**: Spring 生态的 Agentic AI 全栈框架，不只是"模型接入"——它自带完整的 Agent 编排能力。

**三层架构**（v1.1 正式确立）:

```
┌─────────────────────────────┐
│  Agent Framework (应用层)     │  ReactAgent, 上下文工程, HITL
├─────────────────────────────┤
│  Graph (运行时层)             │  StateGraph, 条件边, 子图, 流式
├─────────────────────────────┤
│  Augmented LLM (基础层)      │  ChatModel, Tool, MCP, 向量存储
└─────────────────────────────┘
```

**核心能力**:

| 维度 | 能力 |
|------|------|
| **图编排** | `StateGraph` + 节点 + 条件边 + 并行边，设计理念借鉴 LangGraph |
| **多 Agent** | 3 种模式：Agent as Tool / 工作流 Handoff / Agent as Workflow Node |
| **流式输出** | `CompiledGraph.stream()` 原生支持 `Flux<NodeOutput>`，每个节点流式推送 |
| **状态管理** | `MemorySaver`（短期）+ `RedisSaver` / `MongoSaver`（生产）+ `MemoryStore`（长期） |
| **上下文工程** | 7 大内置 Hook/Interceptor：HITL、Planning、成本控制、重试、工具选择 |
| **模型支持** | 继承 Spring AI 全部模型：DashScope(Qwen)、DeepSeek、OpenAI、Claude、Ollama 等 |
| **企业集成** | Spring Security、Actuator、RBAC、审计日志 |
| **工作流 Agent** | `SequentialAgent` / `ParallelAgent` / `LlmRoutingAgent` 三种内置类型 |

**代码示例 — 构建 6 阶段管线**:

```java
// 阶段节点
AsyncNodeAction<GenState> analysisNode = node_async(this::analyzeRequirement);
AsyncNodeAction<GenState> planNode = node_async(this::planExecution);
AsyncNodeAction<GenState> codeGenNode = node_async(this::generateCode);
AsyncNodeAction<GenState> styleNode = node_async(this::optimizeStyle);
AsyncNodeAction<GenState> buildNode = node_async(this::buildVerify);
AsyncNodeAction<GenState> previewNode = node_async(this::previewVerify);

// 构建状态图
StateGraph pipeline = new StateGraph("code-gen-pipeline", GenState.SCHEMA)
    .addNode("analysis", analysisNode)
    .addNode("plan", planNode)
    .addNode("code_gen", codeGenNode)
    .addNode("style", styleNode)
    .addNode("build", buildNode)
    .addNode("preview", previewNode)
    .addEdge(START, "analysis")
    .addEdge("analysis", "plan")
    .addEdge("plan", "code_gen")
    .addEdge("code_gen", "style")
    .addEdge("style", "build")
    // 构建失败 → 回退到代码生成
    .addConditionalEdges("build", edge_async(this::checkBuildResult),
        Map.of("success", "preview", "fail", "code_gen"))
    .addEdge("preview", END);

CompiledGraph graph = pipeline.compile(CompileConfig.builder()
    .saverConfig(SaverConfig.builder()
        .register(new RedisSaver(redisConnection))
        .build())
    .build());

// 流式推送每个节点的输出到前端
graph.stream(inputs, config)
    .doOnNext(output -> sseEmitter.send(output.toEvent()))
    .subscribe();
```

---

### 2.2 LangGraph4j

**定位**: Python LangGraph 的 Java 移植版，专门做**有状态图编排**。可以对接 LangChain4j 的 ChatModel，也可以对接 Spring AI 的 ChatClient。

**核心能力**:

| 维度 | 能力 |
|------|------|
| **图编排** | `StateGraph` + 异步节点 + 条件边 + 并行边，设计最纯粹 |
| **状态持久化** | `MemorySaver` + `PostgresSaver`，checkpoint 机制完整 |
| **流式** | `stream()` / `streamSnapshots()` 原生支持 |
| **人工介入** | `interruptBefore()` / `interruptAfter()` + `resume()` |
| **可视化** | 生成 PlantUML 图 |
| **模型无关** | 可对接 LangChain4j ChatModel 或 Spring AI ChatClient |

**代码示例 — LangGraph4j 的 6 阶段管线**:

```java
var workflow = new StateGraph<>(GenState.SCHEMA, new StateSerializer())
    .addNode("analysis", node_async(this::analyze))
    .addNode("plan", node_async(this::plan))
    .addNode("code_gen", node_async(this::generate))
    .addNode("style", node_async(this::style))
    .addNode("build", node_async(this::build))
    .addNode("preview", node_async(this::preview))
    .addEdge(START, "analysis")
    .addEdge("analysis", "plan")
    .addEdge("plan", "code_gen")
    .addEdge("code_gen", "style")
    .addEdge("style", "build")
    .addConditionalEdges("build",
        edge_async(state -> state.buildSuccess() ? "preview" : "code_gen"),
        Map.of("preview", "preview", "code_gen", "code_gen"))
    .addEdge("preview", END);

var saver = new PostgresSaver(/* config */);
var graph = workflow.compile(CompileConfig.builder()
    .checkpointSaver(saver).build());

// 流式执行
for (var step : graph.stream(inputs, config)) {
    sseEmitter.send(step.toEvent());
}
```

**关键差异**:

- LangGraph4j 本身的 ChatModel 需要依赖 LangChain4j 的 `ChatLanguageModel` 或 Spring AI 的 `ChatClient`
- 它**不提供** LLM 接入层——你需要额外引入 LangChain4j 或 Spring AI 来做模型调用
- 它**不提供**内置 Agent（如 ReactAgent），需要自己实现推理循环

---

### 2.3 LangChain4j

**定位**: Java 生态最成熟的通用 LLM 应用框架，类似 Python LangChain 的 Java 版。

**核心能力**:

| 维度 | 能力 |
|------|------|
| **模型接入** | 15+ 厂商统一 API（OpenAI, Claude, DeepSeek, Ollama 等） |
| **Agent** | `AiServices` + `@Tool` 注解，`P2PPlanner` 规划器 |
| **流式** | `StreamingChatLanguageModel` + `TokenStream` |
| **RAG** | 完整的文档加载 → 分割 → 嵌入 → 检索链 |
| **A2A** | `AgenticServices.sequenceBuilder()` 构建顺序 Agent 链 |
| **生态** | 社区最活跃（2527 snippets, GitHub 25k+ stars），Quarkus/Micronaut 都支持 |

**代码示例 — LangChain4j 的顺序 Agent 链**:

```java
StreamingCreativeWriter writer = AgenticServices.agentBuilder(StreamingCreativeWriter.class)
    .streamingChatModel(model).outputKey("result").build();

StreamingEditor editor = AgenticServices.agentBuilder(StreamingEditor.class)
    .streamingChatModel(model).outputKey("result").build();

StreamingPipeline pipeline = AgenticServices.sequenceBuilder(StreamingPipeline.class)
    .subAgents(writer, editor)
    .outputKey("result").build();
```

**关键差异**:

- `AiServices` 的 Agent 编排基于**链式串联**，不是图结构
- 没有原生的 **条件回退** 和 **checkpoint 持久化**
- 复杂工作流（如构建失败 → 回退到代码生成）需要自己在业务层实现循环逻辑

---

### 2.4 Spring AI（vanilla）

**定位**: Spring 官方 LLM 集成层，类似 Spring Data 的定位。

**核心能力**:

| 维度 | 能力 |
|------|------|
| **模型接入** | `ChatClient` 统一 API，支持 OpenAI/Claude/Gemini 等国际模型 |
| **Streaming** | `Flux<ChatResponse>` 流式响应 |
| **RAG** | `Document` → `TextSplitter` → 向量存储抽象 |
| **MCP** | MCP Client 支持 |
| **Agent** | 基础的 Tool Calling，**不是核心卖点** |

**关键不足**（对本项目而言）:

- **无 Graph 编排**: 没有 StateGraph、条件边、子图
- **无多 Agent**: 没有 ReactAgent、SequentialAgent、A2A
- **国产模型支持弱**: DeepSeek、Qwen 需要通过 OpenAI 兼容接口手动配置，不如 Spring AI Alibaba 开箱即用
- **状态管理弱**: 只有基础的对话记忆，无 checkpoint/持久化

---

## 三、核心维度对比矩阵

根据 [Context7 实时文档](https://github.com/langgraph4j/langgraph4j)、[阿里云开发者对比报告](https://developer.aliyun.com/article/1686695)、[掘金社区对比分析](https://juejin.cn/post/7491963395284189221) 综合评估：

| 维度 | 方案 A (SAA 统一) | 方案 B (SAA+LG4j) | 方案 C (SAA+LC4j) | 方案 D (SAI+LG4j) |
|------|:--:|:--:|:--:|:--:|
| **国产模型适配** | ★★★★★ | ★★★★★ | ★★★★★ | ★★★ |
| **Graph 编排** | ★★★★ | ★★★★★ | ★★★ | ★★★★★ |
| **多 Agent 协作** | ★★★★★ | ★★★★ | ★★★★ | ★★★★ |
| **流式输出** | ★★★★★ | ★★★★★ | ★★★★ | ★★★★★ |
| **状态管理** | ★★★★ | ★★★★★ | ★★★ | ★★★★★ |
| **企业集成** | ★★★★★ | ★★★★ | ★★★ | ★★★ |
| **中文文档/社区** | ★★★★★ | ★★★ | ★★★★ | ★★★ |
| **单仓库依赖** | ★★★★★ | ★★★ | ★★★ | ★★★ |
| **生产就绪度** | ★★★★ | ★★★★ | ★★★★★ | ★★★ |

---

## 四、与 LingmaForge 6 阶段管线的逐阶段匹配度

| 管线阶段 | 方案 A (SAA 统一) | 方案 B (SA-A+LG4j) | 方案 C (SA-A+LC4j) | 方案 D (SAI+LG4j) |
|----------|:--:|:--:|:--:|:--:|
| ① 需求分析 | ✅ ReactAgent | △ prompt only | △ prompt only | △ prompt only |
| ② 执行规划 | ✅ SequentialAgent | ✅ StateGraph node | △ A2A sequence | ✅ StateGraph node |
| ③ 代码生成 | ✅ ReactAgent+Tool | ✅ agent+tools node | △ @Tool 注解 | △ 国产模型配置差 |
| ④ 样式优化 | ✅ ReactAgent | ✅ agent node | △ @Tool 注解 | △ 国产模型配置差 |
| ⑤ 构建验证 | △ 外部工具节点 | ✅ tool node | ❌ 需自行实现 | ✅ tool node |
| ⑥ 预览+回退 | △ 条件判断 | ✅ checkpoint | ❌ 无原生循环 | ✅ checkpoint |
| **综合匹配** | **★★★★** | **★★★★★** | **★★★** | **★★★** |

**方案 B（SA-A + LangGraph4j）在管线匹配度上最优**——因为 LangGraph4j 的 checkpoint/状态管理是为这种"多阶段+条件回退"场景原生设计的。但方案 A 差距很小，且性价比更高。

---

## 五、最终推荐与理由

### 🏆 推荐: 方案 A — Spring AI Alibaba 统一方案

**一句话**: 用**一个框架**同时解决 LLM 接入和 Agent 编排，以最低的依赖复杂度获得接近 LangGraph4j 的编排能力。

### 五个核心理由

**1. 统一依赖树，零版本冲突**

方案 B/C/D 都需要维护两个框架的版本兼容性。Spring AI Alibaba 一个 `spring-ai-alibaba-agent-framework` 依赖就包含了接入 + 编排的全部能力，pom.xml 干净，升级路径清晰。

**2. 国产模型一等公民**

DeepSeek、Qwen（DashScope）在 Spring AI Alibaba 中是开箱即用的 `spring.ai.dashscope.api-key=xxx` 配置，不需要通过 OpenAI 兼容接口曲折接入。对于主要面向国内用户的灵码工坊，这个差异直接影响开发效率。

**3. Graph 编排能力已成熟**

v1.1 的 Graph 模块已经具备 StateGraph、条件边、并行边、子图嵌套、流式输出、Redis 持久化——与 LangGraph4j 的差距已经从"缺功能"缩小到"某些细节不如"。对于 6 阶段代码生成管线，完全够用。

**4. 内置 Agent 极大降低开发量**

`ReactAgent` 是开箱即用的 Reasoning-Acting 循环 Agent，`SequentialAgent` 直接覆盖管线中的顺序阶段。而 LangGraph4j 每个节点都需要自己实现 Agent 推理循环——对于需求分析/代码生成/样式优化这几个纯 AI 阶段，Spring AI Alibaba 的开发量明显更少。

**5. 阿里云/企业级生态**

Spring Security、Actuator 监控、阿里云 OSS/函数计算——这些都是灵码工坊走向商业化的基础设施。LangGraph4j 需要自己搭建这些。

### 方案 A 的风险与应对

| 风险 | 应对 |
|------|------|
| Graph 状态管理不如 LangGraph4j 精细 | 使用 `RedisSaver` 持久化，关键阶段写入数据库日志 |
| 社区相对年轻（v1.1 发布不到一年） | 已有多家企业在生产中使用，关键路径有 Fallback |
| 长期与阿里生态绑定 | 底层 Spring AI 本身模型无关，核心编排逻辑抽象为接口，未来可切换 |

### 如果条件允许的备选: 方案 B

如果你的团队追求**最大编排灵活性**、可以接受维护两个框架的版本兼容，或者已经在用 LangChain4j 生态——方案 B（Spring AI Alibaba + LangGraph4j）是管线匹配度最高的选择。但需要额外投入：

- 维护 LangGraph4j 的版本与 SA-A 的 LLM Client 版本兼容
- 每个节点手动实现 Agent 推理循环（ReactAgent 的便利性无法复用）

---

## 六、最终技术栈（修订版）

基于以上分析，将原方案中的「Spring AI + LangChain4j」修订为：

| 层级 | 原方案 | 修订方案 | 理由 |
|------|--------|---------|------|
| **AI 接入** | Spring AI 1.0+ | **Spring AI Alibaba 1.1+** | 国产模型开箱即用,同时兼容国际模型 |
| **AI 编排** | LangChain4j 0.36+ | **Spring AI Alibaba Graph + ReactAgent** | 一个框架内完成,Graph 编排能力满足需求,内置 Agent 减少开发量 |
| Prompt 管理 | LangChain4j Template | **Spring AI Alibaba Prompt Template** | 统一框架,无需维护两套模板 |
| **MCP 协议** | Spring AI MCP | **Spring AI Alibaba (继承自 Spring AI)** | 同样支持 MCP Client |

**核心 Maven 依赖**:

```xml
<!-- AI 接入：DashScope（Qwen）+ DeepSeek + OpenAI + Claude 全部覆盖 -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    <version>1.1.2.2</version>
</dependency>

<!-- AI 编排：Graph + ReactAgent + 多Agent协作 -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
    <version>1.1.2.2</version>
</dependency>

<!-- 可视化调试（可选） -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-studio</artifactId>
    <version>1.1.2.2</version>
</dependency>
```

### 降级策略

如果 Spring AI Alibaba Graph 在某些极端场景下不够用，预留了接口层：

```java
public interface CodeGenOrchestrator {
    Flux<GenerationEvent> execute(GenerationRequest request);
    void cancel(String sessionId);
}

// 默认实现: Spring AI Alibaba Graph
@Component("saaCodeGenOrchestrator")
@ConditionalOnProperty(name = "lingma.orchestrator", havingValue = "saa", matchIfMissing = true)
public class SaaCodeGenOrchestrator implements CodeGenOrchestrator { ... }

// 备选实现: LangGraph4j（按需激活）
@Component("lg4jCodeGenOrchestrator")
@ConditionalOnProperty(name = "lingma.orchestrator", havingValue = "langgraph4j")
public class Lg4jCodeGenOrchestrator implements CodeGenOrchestrator { ... }
```

通过 `lingma.orchestrator=langgraph4j` 配置即可切换，避免框架锁定。

---

## 七、参考来源

| 来源 | 内容 |
|------|------|
| [Spring AI Alibaba 官方文档](https://java2ai.com) | Graph、ReactAgent、多Agent、上下文工程 |
| [Spring AI Alibaba GitHub](https://github.com/alibaba/spring-ai-alibaba) | 源码、示例、版本发布 |
| [LangGraph4j GitHub](https://github.com/langgraph4j/langgraph4j) | StateGraph、Checkpoint、Streaming |
| [LangChain4j 官方文档](https://docs.langchain4j.dev) | AiServices、Agent、A2A |
| [阿里云开发者 — 四框架对比](https://developer.aliyun.com/article/1686695) | Spring AI Alibaba vs LangGraph vs LangChain vs Dify |
| [掘金 — Java AI 框架对比](https://juejin.cn/post/7491963395284189221) | LangChain4j vs Spring AI vs Spring AI Alibaba |
| [GitHub java-ai 对比项目](https://github.com/zhouByte-hub/java-ai) | 四框架特性/社区/成熟度矩阵 |
| [Spring AI Alibaba 1.1 全面解读](https://zhuanlan.zhihu.com/p/1974216553265403781) | v1.1 新特性详解 |
