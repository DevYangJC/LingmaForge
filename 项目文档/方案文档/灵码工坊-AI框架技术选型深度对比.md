# 灵码工坊 · AI 框架技术选型深度对比

> **文档版本**: v2.0 | **日期**: 2026-06-26
> **背景**: 在方案设计中被提示 `Spring AI Alibaba` 和 `LangGraph4j` 也是值得关注的选择。本文基于 Context7 实时文档查询 + 多个权威对比来源，对四个框架进行全面评估。
> **结论**: 推荐 **LangChain4j + LangGraph4j 组合方案**（LangChain4j 做 LLM 接入，LangGraph4j 做图编排）。

---

## 一、候选方案概览

| 方案 | AI 接入层 | AI 编排层 | 依赖数量 |
|------|----------|----------|---------|
| 方案 A | Spring AI Alibaba | Spring AI Alibaba Graph + ReactAgent | 1 个全家桶 |
| **方案 B (推荐)** | **LangChain4j** | **LangGraph4j** | **2 个框架** |
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

**关键优势**: 一个框架覆盖三层（接入 + 编排 + Agent），依赖最少，国产模型开箱即用。

**关键不足**: ReactAgent 是黑盒——内部封装了推理循环，不利于学习 Agent 底层原理；Graph 状态管理不如 LangGraph4j 精细（没有 Channel/Reducer 机制）；社区相对年轻。

---

### 2.2 LangGraph4j（1.8 版本）

**定位**: Python LangGraph 的 Java 移植版，专门做**有状态图编排**。设计哲学是"把控制权交给开发者"——不隐藏 Agent 内部逻辑，而是用图显式表达每一步。

**核心能力**:

| 维度 | 能力 |
|------|------|
| **图编排** | `StateGraph` + 异步节点 + 条件边 + 并行边，设计最纯粹 |
| **状态管理** | `AgentState` + `Channel` + `Reducer`——每个状态键可定义合并策略（追加 vs 覆盖） |
| **状态持久化** | `MemorySaver`（内存）+ 可扩展自定义 Saver，checkpoint 机制完整 |
| **流式** | `stream()` 返回 `AsyncGenerator`，逐节点流式输出 |
| **人工介入** | `interruptBefore()` / `interruptAfter()` + `resume()` |
| **可视化** | 生成 PlantUML / Mermaid 图 |
| **模型无关** | 可对接 LangChain4j `ChatModel` 或 Spring AI `ChatClient` |
| **与 LangChain4j 深度集成** | `langgraph4j-langchain4j` 模块提供状态序列化、工具节点等桥接能力 |

**代码示例 — LangGraph4j 的 Agent 循环图**:

```java
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

// 路由条件：模型是否要求调用工具
EdgeAction<CodeGenState> routeMessage = state -> {
    var lastMessage = state.lastMessage();
    if (lastMessage.isEmpty()) return "exit";
    if (lastMessage.get() instanceof AiMessage message) {
        if (message.hasToolExecutionRequests()) return "next";  // 要调工具 → 去 tools 节点
    }
    return "exit";  // 不调工具 → 结束
};

// agent 节点：调 LLM
NodeAction<CodeGenState> callModel = state -> {
    var response = model.chat(ChatRequest.builder()
        .messages(state.messages())
        .toolSpecifications(toolSpecs)
        .build());
    return Map.of("messages", response.aiMessage());
};

// tools 节点：执行工具
NodeAction<CodeGenState> invokeTools = state -> {
    var aiMessage = (AiMessage) state.lastMessage().orElseThrow();
    var toolNode = ToolNode.of(new CodeGenTools());
    var result = toolNode.execute(aiMessage.toolExecutionRequests());
    return Map.of("messages", result);
};

// 构建图：agent → (条件) → tools → agent → (条件) → END
var workflow = new StateGraph<>(CodeGenState.SCHEMA, CodeGenState::new)
    .addNode("agent", node_async(callModel))
    .addNode("tools", node_async(invokeTools))
    .addEdge(START, "agent")
    .addConditionalEdges("agent", edge_async(routeMessage),
        Map.of("next", "tools", "exit", END))
    .addEdge("tools", "agent");  // 工具执行完回到 agent

var graph = workflow.compile(CompileConfig.builder()
    .checkpointSaver(new MemorySaver())
    .build());
```

**关键优势**: 图编排能力最强；状态管理最精细（Channel + Reducer）；Agent 内部逻辑完全透明，非常适合学习。

**关键不足**: 不提供 LLM 接入层——必须搭配 LangChain4j 或 Spring AI 使用；不提供内置 Agent（如 ReactAgent），需要自己用图实现推理循环。

---

### 2.3 LangChain4j（1.x 版本）

**定位**: Java 生态最成熟的通用 LLM 应用框架，类似 Python LangChain 的 Java 版。社区最活跃（GitHub 25k+ stars）。

**核心能力**:

| 维度 | 能力 |
|------|------|
| **模型接入** | 15+ 厂商统一 API（OpenAI, Anthropic, DeepSeek, Ollama 等），通过 OpenAI 兼容协议适配国产模型 |
| **结构化输出** | `AiServices` 接口模式——定义 Java 接口 + POJO 返回类型，框架自动生成 JSON Schema 约束 + 反序列化 |
| **工具调用** | `@Tool` 注解——在普通 Java 方法上加注解即注册为工具，无需实现接口 |
| **流式** | `StreamingChatModel` + `TokenStream`，支持 `onPartialResponse` / `onToolExecuted` 等回调 |
| **RAG** | 完整的文档加载 → 分割 → 嵌入 → 检索链 |
| **生态** | 社区最活跃，Quarkus/Micronaut/Spring Boot 都有 Starter |

**代码示例 — LangChain4j 的结构化输出**:

```java
// 定义接口——返回类型就是结构化输出类型
interface RequirementAnalyzer {
    @SystemMessage("你是专业的需求分析师，将用户需求解析为结构化规范")
    RequirementSpec analyze(@UserMessage String userPrompt);
}

// 创建模型（DeepSeek 通过 OpenAI 兼容协议接入）
ChatModel deepSeekModel = OpenAiChatModel.builder()
    .baseUrl("https://api.deepseek.com/v1")
    .apiKey(System.getenv("DEEPSEEK_API_KEY"))
    .modelName("deepseek-chat")
    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
    .strictJsonSchema(true)
    .build();

// 框架自动生成实现
RequirementAnalyzer analyzer = AiServices.create(RequirementAnalyzer.class, deepSeekModel);

// 调用——返回值直接就是 Java 对象，零手动解析
RequirementSpec spec = analyzer.analyze("帮我生成一个会员订阅商城");
```

**代码示例 — LangChain4j 的工具调用**:

```java
// 普通类 + 方法注解，无需实现接口
public class CodeGenTools {
    @Tool("将完整文件内容写入项目工作区指定路径")
    public String writeFile(String path, String content) {
        fileService.write(path, content);
        return "文件已写入: " + path;
    }

    @Tool("读取已生成文件的内容作为上下文")
    public String readFileContext(String path) {
        return fileService.read(path);
    }
}

// 注册到 AI Service
CodeGenerator generator = AiServices.builder(CodeGenerator.class)
    .chatModel(claudeModel)
    .tools(new CodeGenTools())
    .build();
```

**关键优势**: 结构化输出最优雅（接口模式比 `.entity()` 更类型安全）；`@Tool` 注解极简；社区最活跃文档最全。

**关键不足**: Agent 编排基于链式串联，不是图结构；没有原生的条件回退和 checkpoint 持久化；复杂工作流需要配合 LangGraph4j。

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
- **国产模型支持弱**: DeepSeek、Qwen 需要通过 OpenAI 兼容接口手动配置
- **状态管理弱**: 只有基础的对话记忆，无 checkpoint/持久化

---

## 三、核心维度对比矩阵

根据 [Context7 实时文档](https://github.com/langgraph4j/langgraph4j)、[LangChain4j 官方文档](https://docs.langchain4j.dev) 综合评估：

| 维度 | 方案 A (SAA 统一) | 方案 B (LC4j+LG4j) | 方案 C (SAA+LC4j) | 方案 D (SAI+LG4j) |
|------|:--:|:--:|:--:|:--:|
| **国产模型适配** | ★★★★★ | ★★★★ | ★★★★★ | ★★★ |
| **Graph 编排** | ★★★★ | ★★★★★ | ★★★ | ★★★★★ |
| **状态管理精细度** | ★★★★ | ★★★★★ | ★★★ | ★★★★★ |
| **结构化输出** | ★★★★ | ★★★★★ | ★★★★ | ★★★ |
| **工具调用简洁度** | ★★★ | ★★★★★ | ★★★★ | ★★★ |
| **流式输出** | ★★★★★ | ★★★★★ | ★★★★ | ★★★★★ |
| **Agent 透明度** | ★★★ | ★★★★★ | ★★★★ | ★★★★★ |
| **社区/文档** | ★★★★ | ★★★★★ | ★★★ | ★★★ |
| **学习价值** | ★★★ | ★★★★★ | ★★★ | ★★★★ |
| **单仓库依赖** | ★★★★★ | ★★★ | ★★★ | ★★★ |

---

## 四、与 LingmaForge 6 阶段管线的逐阶段匹配度

| 管线阶段 | 方案 A (SAA 统一) | 方案 B (LC4j+LG4j) | 方案 C (SAA+LC4j) | 方案 D (SAI+LG4j) |
|----------|:--:|:--:|:--:|:--:|
| ① 需求分析 | ✅ ReactAgent | ✅ AiServices + 节点 | △ prompt only | △ prompt only |
| ② 执行规划 | ✅ SequentialAgent | ✅ AiServices + 节点 | △ A2A sequence | ✅ 节点 |
| ③ 代码生成 | ✅ ReactAgent+Tool | ✅ @Tool + agent→tools 循环图 | △ @Tool 注解 | △ 国产模型配置差 |
| ④ 样式优化 | ✅ ReactAgent | ✅ @Tool + 节点 | △ @Tool 注解 | △ 国产模型配置差 |
| ⑤ 构建验证 | △ 外部工具节点 | ✅ 纯逻辑节点 + 条件边 | ❌ 需自行实现 | ✅ 纯逻辑节点 |
| ⑥ 预览+回退 | △ 条件判断 | ✅ checkpoint + 条件边 | ❌ 无原生循环 | ✅ checkpoint |
| **综合匹配** | **★★★★** | **★★★★★** | **★★★** | **★★★** |

---

## 五、最终推荐与理由

### 推荐: 方案 B — LangChain4j + LangGraph4j 组合方案

**一句话**: LangChain4j 负责 LLM 接入和工具调用，LangGraph4j 负责图式编排和状态管理。两个框架各司其职、边界清晰。

### 五个核心理由

**1. 学习价值最高**

这是本项目选择此方案的关键因素之一。Spring AI Alibaba 的 `ReactAgent` 是黑盒——内部封装了推理循环，你看不到模型怎么决策、工具怎么调用。而 LangGraph4j 要求你**用图显式表达 Agent 循环**——`agent → (条件路由) → tools → agent → ...`——每一步都可见、可调试、可定制。对于教学项目，这种透明度至关重要：你不仅是在用框架，更是在理解 Agent 的本质。

**2. 编排能力最强**

LangGraph4j 的 `StateGraph` 是四个框架中图编排能力最纯粹的——条件边、并行边、子图嵌套、checkpoint 持久化、中断恢复、人工介入全部原生支持。对于灵码工坊的 6 阶段管线 + 构建失败回退场景，LangGraph4j 的条件边和 checkpoint 是原生设计，不是"补丁"。

**3. 状态管理最精细**

LangGraph4j 的 `AgentState` + `Channel` + `Reducer` 机制允许你为每个状态键定义合并策略——`messages` 用追加模式（AppenderChannel），`analysisResult` 用覆盖模式。这在多 Agent 协作场景中非常重要——代码生成节点每次生成一个文件就追加到 `files` 列表，不需要手动读取旧值再拼接。

**4. LangChain4j 的 API 设计最优雅**

- **结构化输出**：`AiServices` 接口模式比 Spring AI 的 `.entity()` 更类型安全——你定义一个接口和返回类型，框架自动生成实现，像 MyBatis Mapper 一样自然
- **工具调用**：`@Tool` 注解在一个普通 Java 方法上即可注册为工具，无需实现 `ToolCallback` 接口，代码量减半
- **流式输出**：`TokenStream` + `onPartialResponse` / `onToolExecuted` 回调链设计清晰
- **多模型支持**：DeepSeek / Qwen 通过 OpenAI 兼容协议接入，Claude 用原生 Anthropic 适配器，切换只需换 Builder

**5. 社区最活跃，文档最全**

LangChain4j GitHub 25k+ stars，Context7 上有 1490+ 代码示例。LangGraph4j 有 1490+ 代码示例，High reputation。遇到问题能找到答案，而不是去翻框架源码。

### 需要接受的代价

| 代价 | 应对 |
|------|------|
| 维护两个框架的版本兼容 | LangGraph4j 提供 `langgraph4j-langchain4j` 桥接模块，版本对齐有 BOM 管理 |
| 没有内置 ReactAgent，需手写循环图 | 这正是学习价值所在——用 StateGraph 的 `agent → tools → agent` 循环显式表达推理过程 |
| 国产模型需通过 OpenAI 兼容协议接入 | DeepSeek / Qwen 都原生兼容 OpenAI 协议，LangChain4j 的 `OpenAiChatModel` 配置 `baseUrl` 即可 |

---

## 六、最终技术栈

| 层级 | 技术 | 版本 | 做什么 |
|------|------|------|--------|
| **AI 接入** | LangChain4j | 1.x | LLM 统一接入（DeepSeek/Claude/Qwen） |
| **AI 编排** | LangGraph4j | 1.8+ | StateGraph 图式工作流 + 状态管理 |
| **桥接** | langgraph4j-langchain4j | 1.8+ | LangChain4j 状态序列化 + 工具节点 |
| **结构化输出** | LangChain4j AiServices | — | 接口模式，返回类型即结构化类型 |
| **工具调用** | LangChain4j @Tool | — | 方法注解，无需实现接口 |
| **Prompt 管理** | LangChain4j @SystemMessage | — | 注解 + 模板文件双模式 |

**核心 Maven 依赖**:

```xml
<properties>
    <langchain4j.version>1.16.2</langchain4j.version>
    <langgraph4j.version>1.8.19</langgraph4j.version>
</properties>

<!-- LangChain4j BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>${langchain4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- LangGraph4j BOM -->
        <dependency>
            <groupId>org.bsc.langgraph4j</groupId>
            <artifactId>langgraph4j-bom</artifactId>
            <version>${langgraph4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- LangChain4j 核心 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
    </dependency>
    <!-- OpenAI 兼容（DeepSeek / Qwen / GPT） -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
    </dependency>
    <!-- Anthropic（Claude） -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-anthropic</artifactId>
    </dependency>

    <!-- LangGraph4j 核心 -->
    <dependency>
        <groupId>org.bsc.langgraph4j</groupId>
        <artifactId>langgraph4j-core</artifactId>
    </dependency>
    <!-- LangGraph4j ↔ LangChain4j 桥接（状态序列化、工具节点） -->
    <dependency>
        <groupId>org.bsc.langgraph4j</groupId>
        <artifactId>langgraph4j-langchain4j</artifactId>
    </dependency>
</dependencies>
```

---

## 七、API 对照表：Spring AI Alibaba → LangChain4j + LangGraph4j

| 功能 | Spring AI Alibaba (旧) | LangChain4j + LangGraph4j (新) |
|------|----------------------|-------------------------------|
| **结构化输出** | `ChatClient.entity(Class)` | `AiServices.create(Interface.class, model)` |
| **工具定义** | `implements ToolCallback` | `@Tool` 注解方法 |
| **Agent 推理循环** | `ReactAgent`（内置黑盒） | StateGraph 的 `agent → tools → agent` 循环图（白盒） |
| **工作流编排** | `StateGraph` (SAA Graph) | `StateGraph` (LangGraph4j) |
| **状态管理** | `OverAllState (Map<String,Object>)` | `AgentState` + `Channel` + `Reducer` |
| **状态持久化** | `RedisSaver` | `MemorySaver` / 自定义 Saver |
| **流式输出** | `CompiledGraph.stream()` → `Flux<NodeOutput>` | `graph.stream()` → `AsyncGenerator<NodeOutput>` |
| **模型配置** | `spring.ai.dashscope.*` (yml) | `OpenAiChatModel.builder()` (Java) |
| **多模型切换** | `@Qualifier` 注解 | 不同 Builder 实例 |
| **提示词** | Prompt Template 文件 | `@SystemMessage` 注解 + 模板文件 |

---

## 八、参考来源

| 来源 | 内容 |
|------|------|
| [LangGraph4j GitHub](https://github.com/langgraph4j/langgraph4j) | StateGraph、Checkpoint、Streaming、Agent 循环 |
| [LangChain4j 官方文档](https://docs.langchain4j.dev) | AiServices、@Tool、TokenStream、结构化输出 |
| [LangGraph4j how-tos](https://github.com/langgraph4j/langgraph4j/tree/main/how-tos) | 完整示例：agentexecutor、time-travel、issue51 |
| [Spring AI Alibaba 官方文档](https://java2ai.com) | Graph、ReactAgent、多Agent、上下文工程 |
| [阿里云开发者 — 四框架对比](https://developer.aliyun.com/article/1686695) | Spring AI Alibaba vs LangGraph vs LangChain vs Dify |
| [掘金 — Java AI 框架对比](https://juejin.cn/post/7491963395284189221) | LangChain4j vs Spring AI vs Spring AI Alibaba |
| [GitHub java-ai 对比项目](https://github.com/zhouByte-hub/java-ai) | 四框架特性/社区/成熟度矩阵 |
