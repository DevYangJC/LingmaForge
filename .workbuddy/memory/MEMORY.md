# 灵码工坊项目记忆

## AI 框架选型（2026-06-26 最终决定）
- **选定框架**: LangChain4j + LangGraph4j（而非 Spring AI Alibaba）
- **决策原因**: 学习价值 + 白盒 Agent 透明性 + 最强图编排 + 最活跃社区
- **Maven 依赖**: `dev.langchain4j:langchain4j-bom:1.16.2` + `org.bsc.langgraph4j:langgraph4j-bom:1.8.19`

## 核心代码模式映射
| Spring AI Alibaba | LangChain4j + LangGraph4j |
|---|---|
| `ChatClient.builder()` + `.entity(Class)` | `AiServices.create(Interface.class, model)` |
| `implements ToolCallback` | `@Tool` 注解普通方法 |
| `ReactAgent.builder()` | `StateGraph` + `agent→tools→agent` 循环图 |
| `OverAllState (Map<String,Object>)` | `AgentState` + `Channel` + `Reducer` |
| `GenerationStateKeys` + `GenerationStateAccessor` | `CodeGenState extends AgentState` |
| `AbstractReactAgentNode` 基类 | 直接 `NodeAction` 返回 `Map<String, Object>` |
| `state.put(key, value)` | 返回 `Map.of(key, value)`，Reducer 自动合并 |

## 六阶段流水线
1. requirement_analysis → DeepSeek-V3 (AiServices 单次调用)
2. execution_planning → DeepSeek-V3 (AiServices 单次调用)
3. code_generation → Claude Sonnet 4 (agent→tools→agent 循环图)
4. style_optimization → DeepSeek-V3 (agent→tools→agent 循环图)
5. build_verification → 纯逻辑 (SandboxService)
6. preview_deploy → 纯逻辑 (SandboxService)
- 条件边: buildVerification → FAILED → 回退 codeGeneration（最多 3 次）

## @Tool 工具分组（按业务域）
- FileTools: writeFile + patchFile + validateCode
- ProjectContextTools: readProjectContext + readFileContext
- IterationTools: searchCode

## 模型配置
- DeepSeek: `OpenAiChatModel.builder().baseUrl("https://api.deepseek.com/v1").modelName("deepseek-chat").strictJsonSchema(true)`
- Claude: `AnthropicChatModel.builder().modelName("claude-sonnet-4-20250514")`

## 文档状态（v3.0 全部完成）
- 灵码工坊-AI框架技术选型深度对比.md → v2.0 ✅
- 灵工坊-AI代码生成核心功能架构方案.md → v4.0 ✅
- 灵码工坊-后端核心功能实现指南.md → v3.0 ✅
