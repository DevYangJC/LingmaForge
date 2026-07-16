# Dialogue Iteration Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first Cursor-like closed loop: users can ask for project changes in the IDE chat, the backend plans and applies file modifications, verifies the build, and streams progress back to the frontend.

**Architecture:** Keep the first version as a fixed LangGraph4j iteration pipeline instead of a full supervisor. Add serializable state models, extend the shared blackboard, introduce focused iteration nodes, and wire the existing REST/SSE entrypoints to the new pipeline. Keep model selection, prompt loading, event publishing, and file mutation behind small interfaces so supervisor, evaluator, and strategy modes can be added later.

**Tech Stack:** Java 21, Spring Boot 3.5, LangGraph4j 1.8, LangChain4j 1.16, JUnit 5, AssertJ, Vue 3, Pinia, SSE.

---

## Scope

This plan implements the first usable backend-first MVP:

- Conversation request creates an iteration task.
- Iteration pipeline loads project context.
- Agent produces a structured modification plan.
- File changes are applied through existing file tools/services.
- Build verification runs after changes.
- SSE emits node progress, file changes, build logs, completion, and errors.
- Frontend chat only needs minimal wiring to submit iteration requests and consume the existing iteration SSE stream.

This plan deliberately does not implement a dynamic supervisor, vector retrieval, multi-agent parallel execution, user approval before applying patches, or long-term memory. Those become later phases after the closed loop is stable.

---

## File Map

### Backend Model And State

- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/pipeline/CodeGenState.java`
  - Add iteration state keys and typed accessors, including a compact `iterationContext` string used between context loading and planning.
  - Keep Chinese Javadoc on every new key/accessor.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/IterationIntent.java`
  - Serializable record describing the user's modification intent.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/ModificationPlan.java`
  - Serializable record containing summary, affected files, and execution notes.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/FileChangePlan.java`
  - Serializable record for one planned file change.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/FileChangeResult.java`
  - Serializable record for one applied file change.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/BuildErrorAnalysis.java`
  - Serializable record for build failure classification and suggested fix.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/QualityReviewResult.java`
  - Serializable record for later quality checks; first version stores basic pass/warning data.
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/common/model/CommonModelSerializationTest.java`
  - Extend the existing serialization coverage to include new model types.

### Backend Pipeline And Nodes

- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/pipeline/IterationPipeline.java`
  - LangGraph4j graph dedicated to project iteration.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/IterationIntentAnalysisNode.java`
  - Converts user prompt into `IterationIntent`.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/ProjectContextLoadNode.java`
  - Loads framework, file tree, dependency list, and relevant file contents.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/ModificationPlanningNode.java`
  - Produces `ModificationPlan`.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/CodePatchNode.java`
  - Applies planned file changes through services/tools and emits file events.
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/BuildErrorAnalysisNode.java`
  - Converts build errors into `BuildErrorAnalysis` before retrying.
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/BuildVerificationNode.java`
  - Reuse the existing node directly for iteration verification; only change it if tests reveal generation-only assumptions.
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/pipeline/IterationPipelineTest.java`
  - Covers graph routing and retry decisions.
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/pipeline/IterationNodesTest.java`
  - Covers node-level state updates with mocked agents/services.

### Backend Agent Interfaces

- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/service/IterationAgent.java`
  - Replace or extend free-form `modify(String)` with structured methods.
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/factory/AgentFactory.java`
  - Keep `createIterationAgent()` as the single factory method for this phase and make it return an agent with the structured methods below.
  - Add or update prompt templates for intent analysis, modification planning, and build error analysis.

### Backend Service And API

- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/service/GenerationService.java`
  - Replace the current direct `IterationAgent` short flow with `IterationPipeline`.
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/web/GenerationController.java`
  - Keep existing `/api/generation/iterate` and `/api/stream/iteration/{taskId}` endpoints.
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/observer/GenerationStreamEmitter.java`
  - Add explicit event methods only if existing methods cannot express a new event.
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/workbench/service/GenerationServiceIterationTest.java`
  - Verifies iteration streams use `IterationPipeline`.

### Frontend

- Modify: `lingmaForge-frontend/src/components/workbench/GenerationMode.vue`
  - Wire chat submit to iteration endpoint.
  - Subscribe to `/api/stream/iteration/{taskId}`.
  - Render node messages, file modifications, build logs, and completion.
- Modify: `lingmaForge-frontend/src/stores/workbench.ts`
  - Add iteration task state if not already present.
- Test: run `npm test` and `npm run type-check` in `lingmaForge-frontend`.

---

## Task 1: Serializable Iteration Models

**Files:**
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/IterationIntent.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/ModificationPlan.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/FileChangePlan.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/FileChangeResult.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/BuildErrorAnalysis.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/common/model/QualityReviewResult.java`
- Modify: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/common/model/CommonModelSerializationTest.java`

- [ ] **Step 1: Write failing serialization coverage**

Add the new classes to `CommonModelSerializationTest.serializableTypes()` before implementing them:

```java
IterationIntent.class,
ModificationPlan.class,
FileChangePlan.class,
FileChangeResult.class,
BuildErrorAnalysis.class,
QualityReviewResult.class,
```

- [ ] **Step 2: Run the model test and confirm RED**

Run:

```powershell
.\mvnw.cmd -Dtest=CommonModelSerializationTest test
```

Expected: compilation fails because the six classes do not exist yet.

- [ ] **Step 3: Implement `IterationIntent`**

Create:

```java
package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 用户本轮对话式修改的意图识别结果。
 *
 * @param type 修改类型，例如 style、feature、bugfix、refactor、dependency、unknown
 * @param summary 用户意图的简短中文摘要
 * @param targetFiles 可能需要读取或修改的文件路径列表
 * @param requiresBuild 修改完成后是否需要触发构建验证
 */
public record IterationIntent(
        String type,
        String summary,
        List<String> targetFiles,
        boolean requiresBuild) implements Serializable {

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 4: Implement `FileChangePlan`**

Create `FileChangePlan.java`:

```java
package com.lingmaforge.backend.common.model;

import java.io.Serializable;

/**
 * 单个文件的计划变更项。
 *
 * @param path 文件相对项目根目录的路径
 * @param action 变更动作，取值为 create、update、delete
 * @param reason 执行该变更的原因说明
 * @param newContent create/update 时的新文件完整内容，delete 时为空字符串
 */
public record FileChangePlan(
        String path,
        String action,
        String reason,
        String newContent) implements Serializable {

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 5: Implement `ModificationPlan`**

Create `ModificationPlan.java`:

```java
package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 本轮迭代修改的结构化执行计划。
 *
 * @param summary 修改计划的中文摘要
 * @param changes 待执行的文件变更列表
 * @param risks 执行前识别到的风险或注意事项
 */
public record ModificationPlan(
        String summary,
        List<FileChangePlan> changes,
        List<String> risks) implements Serializable {

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 6: Implement `FileChangeResult`**

Create `FileChangeResult.java`:

```java
package com.lingmaforge.backend.common.model;

import java.io.Serializable;

/**
 * 单个文件变更的执行结果。
 *
 * @param path 文件相对项目根目录的路径
 * @param action 已执行的动作，取值为 create、update、delete
 * @param success 是否执行成功
 * @param message 执行结果说明或失败原因
 */
public record FileChangeResult(
        String path,
        String action,
        boolean success,
        String message) implements Serializable {

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 7: Implement `BuildErrorAnalysis`**

Create `BuildErrorAnalysis.java`:

```java
package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 构建失败后的错误分析结果。
 *
 * @param category 错误分类，例如 syntax、dependency、type、config、unknown
 * @param summary 错误原因的中文摘要
 * @param suspectedFiles 可能导致错误的文件路径列表
 * @param suggestedFix 建议下一轮修复采用的策略
 */
public record BuildErrorAnalysis(
        String category,
        String summary,
        List<String> suspectedFiles,
        String suggestedFix) implements Serializable {

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 8: Implement `QualityReviewResult`**

Create `QualityReviewResult.java`:

```java
package com.lingmaforge.backend.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 生成或修改结果的质量评估结果。
 *
 * @param passed 是否通过本轮质量检查
 * @param score 质量评分，范围 0 到 100
 * @param warnings 不阻断执行的风险提示
 * @param suggestions 后续可优化建议
 */
public record QualityReviewResult(
        boolean passed,
        int score,
        List<String> warnings,
        List<String> suggestions) implements Serializable {

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 9: Run the model test and confirm GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=CommonModelSerializationTest test
```

Expected: test passes. If local Java 21 is unavailable, record the exact environment error and continue only after installing/configuring JDK 21.

---

## Task 2: Extend `CodeGenState` Blackboard

**Files:**
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/pipeline/CodeGenState.java`
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/pipeline/CodeGenStateTest.java`

- [ ] **Step 1: Write failing state tests**

Add assertions that `CodeGenState.channels()` contains:

```java
iterationPrompt
iterationIntent
iterationContext
modificationPlan
modifiedFiles
buildErrorAnalysis
qualityReviewResult
```

Also instantiate `CodeGenState` with these values and verify typed accessors return them.

- [ ] **Step 2: Run state tests and confirm RED**

Run:

```powershell
.\mvnw.cmd -Dtest=CodeGenStateTest test
```

Expected: fails because constants/accessors do not exist.

- [ ] **Step 3: Add state constants, channels, and accessors**

Add constants with Chinese Javadoc:

```java
public static final String ITERATION_PROMPT = "iterationPrompt";
public static final String ITERATION_INTENT = "iterationIntent";
public static final String ITERATION_CONTEXT = "iterationContext";
public static final String MODIFICATION_PLAN = "modificationPlan";
public static final String MODIFIED_FILES = "modifiedFiles";
public static final String BUILD_ERROR_ANALYSIS = "buildErrorAnalysis";
public static final String QUALITY_REVIEW_RESULT = "qualityReviewResult";
```

Add channels:

```java
Map.entry(ITERATION_PROMPT, nullableChannel()),
Map.entry(ITERATION_INTENT, nullableChannel()),
Map.entry(ITERATION_CONTEXT, nullableChannel()),
Map.entry(MODIFICATION_PLAN, nullableChannel()),
Map.entry(MODIFIED_FILES, Channels.appender(ArrayList::new)),
Map.entry(BUILD_ERROR_ANALYSIS, nullableChannel()),
Map.entry(QUALITY_REVIEW_RESULT, nullableChannel())
```

Add typed accessors:

```java
public Optional<String> iterationPrompt() { return value(ITERATION_PROMPT); }
public Optional<IterationIntent> iterationIntent() { return value(ITERATION_INTENT); }
public Optional<String> iterationContext() { return value(ITERATION_CONTEXT); }
public Optional<ModificationPlan> modificationPlan() { return value(MODIFICATION_PLAN); }
public Optional<List<FileChangeResult>> modifiedFiles() { return value(MODIFIED_FILES); }
public Optional<BuildErrorAnalysis> buildErrorAnalysis() { return value(BUILD_ERROR_ANALYSIS); }
public Optional<QualityReviewResult> qualityReviewResult() { return value(QUALITY_REVIEW_RESULT); }
```

- [ ] **Step 4: Run state tests and confirm GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=CodeGenStateTest test
```

Expected: passes.

---

## Task 3: Structured Iteration Agent Contract

**Files:**
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/service/IterationAgent.java`
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/factory/AgentFactory.java`
- Create: `lingmaForge-backend/src/main/resources/prompts/iteration-intent-analysis-system.txt`
- Create: `lingmaForge-backend/src/main/resources/prompts/iteration-intent-analysis-user.txt`
- Create: `lingmaForge-backend/src/main/resources/prompts/iteration-modification-planning-system.txt`
- Create: `lingmaForge-backend/src/main/resources/prompts/iteration-modification-planning-user.txt`
- Create: `lingmaForge-backend/src/main/resources/prompts/build-error-analysis-system.txt`
- Create: `lingmaForge-backend/src/main/resources/prompts/build-error-analysis-user.txt`
- Modify: `lingmaForge-backend/src/main/resources/prompts/iteration-modification-system.txt`
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/service/AgentMockTest.java`

- [ ] **Step 1: Write failing mock-agent tests**

Assert that a mocked `IterationAgent` can expose structured operations:

```java
IterationIntent analyzeIntent(String prompt, String projectContext);
ModificationPlan planModification(String prompt, String projectContext, IterationIntent intent);
BuildErrorAnalysis analyzeBuildError(String buildLog, ModificationPlan plan);
```

- [ ] **Step 2: Run tests and confirm RED**

Run:

```powershell
.\mvnw.cmd -Dtest=AgentMockTest test
```

Expected: compilation fails because the methods are missing.

- [ ] **Step 3: Update the interface**

Add Chinese Javadoc explaining each operation. Keep the older `modify(String)` only while `GenerationService` is still being migrated; after Task 6 switches to `IterationPipeline`, remove direct production usage of `modify(String)`.

- [ ] **Step 4: Create prompt templates**

Create `iteration-intent-analysis-system.txt`:

```text
你是灵码工坊的迭代意图分析智能体。你的任务是把用户的自然语言修改请求归类为结构化意图。只返回 JSON，不要输出 Markdown。
```

Create `iteration-intent-analysis-user.txt`:

```text
用户修改请求：{{prompt}}

项目上下文：
{{projectContext}}

请返回符合 IterationIntent 字段的 JSON：type、summary、targetFiles、requiresBuild。
```

Create `iteration-modification-planning-system.txt`:

```text
你是灵码工坊的修改规划智能体。你的任务是根据用户请求、项目上下文和意图结果，输出文件级修改计划。只返回 JSON，不要输出 Markdown。
```

Create `iteration-modification-planning-user.txt`:

```text
用户修改请求：{{prompt}}

项目上下文：
{{projectContext}}

意图识别结果：
{{iterationIntent}}

请返回符合 ModificationPlan 字段的 JSON：summary、changes、risks。changes 中每一项包含 path、action、reason、newContent。action 只能是 create、update、delete。
```

Create `build-error-analysis-system.txt`:

```text
你是灵码工坊的构建错误分析智能体。你的任务是根据构建日志和本轮修改计划判断失败原因，并给出下一轮定向修复建议。只返回 JSON，不要输出 Markdown。
```

Create `build-error-analysis-user.txt`:

```text
构建日志：
{{buildLog}}

本轮修改计划：
{{modificationPlan}}

请返回符合 BuildErrorAnalysis 字段的 JSON：category、summary、suspectedFiles、suggestedFix。
```

- [ ] **Step 5: Update `AgentFactory`**

Ensure `createIterationAgent()` creates an AiServices proxy that supports the structured methods and has the required tools:

```java
return AiServices.builder(IterationAgent.class)
        .chatModel(selectedModel)
        .tools(fileTools, projectContextTools, iterationTools)
        .build();
```

Use the project's existing `AgentFactory` style instead of introducing a new framework.

- [ ] **Step 6: Run agent tests and confirm GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=AgentMockTest test
```

Expected: passes.

---

## Task 4: Iteration Nodes

**Files:**
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/IterationIntentAnalysisNode.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/ProjectContextLoadNode.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/ModificationPlanningNode.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/CodePatchNode.java`
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/node/BuildErrorAnalysisNode.java`
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/pipeline/IterationNodesTest.java`

- [ ] **Step 1: Write failing node tests**

Write one focused test per node:

```java
Map<String, Object> result = intentNode.execute(state);
assertThat(result).containsKey(CodeGenState.ITERATION_INTENT);
```

```java
Map<String, Object> result = contextNode.execute(state);
assertThat(result).containsKey(CodeGenState.ITERATION_CONTEXT);
```

```java
Map<String, Object> result = planningNode.execute(state);
assertThat(result).containsKey(CodeGenState.MODIFICATION_PLAN);
```

```java
Map<String, Object> result = patchNode.execute(state);
assertThat(result).containsKey(CodeGenState.MODIFIED_FILES);
```

```java
Map<String, Object> result = buildErrorNode.execute(state);
assertThat(result).containsKey(CodeGenState.BUILD_ERROR_ANALYSIS);
```

Use mocks for `IterationAgent`, `ProjectService`, `ProjectFileService`, and `GenerationStreamRegistry`. For `CodePatchNode`, verify action mapping explicitly:

```java
verify(projectFileService).writeFile(projectId, "src/App.vue", newContent, "modified");
verify(projectFileService).deleteFile(projectId, "src/Old.vue");
```

- [ ] **Step 2: Run node tests and confirm RED**

Run:

```powershell
.\mvnw.cmd -Dtest=IterationNodesTest test
```

Expected: compilation fails because node classes do not exist.

- [ ] **Step 3: Implement nodes with one responsibility each**

Each class must:

- Extend or follow `AbstractCodeGenNode` conventions.
- Declare `public static final String NODE_NAME`.
- Have Chinese class Javadoc and method Javadoc.
- Emit `node_start`, `message`, and `node_end` through `GenerationStreamEmitter` when available.
- Return a `Map<String, Object>` update only for fields it owns.

Node ownership:

- `IterationIntentAnalysisNode` writes `ITERATION_INTENT`.
- `ProjectContextLoadNode` writes `ITERATION_CONTEXT` as a compact string containing framework, file paths, dependencies, and selected file contents.
- `ModificationPlanningNode` writes `MODIFICATION_PLAN`.
- `CodePatchNode` maps `FileChangePlan.action` to `ProjectFileService`: `create` and `update` call `writeFile(projectId, path, newContent, status)`, while `delete` calls `deleteFile(projectId, path)`. The MVP uses full-file replacement through `newContent`; line-level patching can be added later through the existing `patchFile` API.
- `BuildErrorAnalysisNode` writes `BUILD_ERROR_ANALYSIS`.

- [ ] **Step 4: Run node tests and confirm GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=IterationNodesTest test
```

Expected: passes.

---

## Task 5: Iteration Pipeline

**Files:**
- Create: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/ai/pipeline/IterationPipeline.java`
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/ai/pipeline/IterationPipelineTest.java`

- [ ] **Step 1: Write failing pipeline tests**

Cover these cases:

- Successful route: intent -> context -> plan -> patch -> build -> preview/end.
- Build success routes to preview or end.
- Build failure routes to build error analysis, then patch, until retry limit.
- Retry limit routes to error end.

- [ ] **Step 2: Run pipeline tests and confirm RED**

Run:

```powershell
.\mvnw.cmd -Dtest=IterationPipelineTest test
```

Expected: compilation fails because `IterationPipeline` does not exist.

- [ ] **Step 3: Implement graph**

Use LangGraph4j like `CodeGenPipeline`:

```text
START
-> iteration_intent_analysis
-> project_context_load
-> modification_planning
-> code_patch
-> build_verification
-> preview_deploy
-> END
```

Conditional edge after `build_verification`:

```text
SUCCESS -> preview_deploy
FAILED and retry <= max -> build_error_analysis -> code_patch -> build_verification
FAILED and retry > max -> error_end
```

- [ ] **Step 4: Run pipeline tests and confirm GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=IterationPipelineTest test
```

Expected: passes.

---

## Task 6: Wire `GenerationService` To Iteration Pipeline

**Files:**
- Modify: `lingmaForge-backend/src/main/java/com/lingmaforge/backend/workbench/service/GenerationService.java`
- Test: `lingmaForge-backend/src/test/java/com/lingmaforge/backend/workbench/service/GenerationServiceIterationTest.java`

- [ ] **Step 1: Write failing service test**

Mock `IterationPipeline.getCompiledGraph().stream(inputs)` and assert `streamIteration(taskId)` passes:

```java
CodeGenState.PROJECT_ID
CodeGenState.TASK_ID
CodeGenState.ITERATION_PROMPT
```

Expected: current implementation calls `IterationAgent` directly, so the test fails.

- [ ] **Step 2: Inject `IterationPipeline`**

Add a constructor dependency:

```java
private final IterationPipeline iterationPipeline;
```

- [ ] **Step 3: Replace `runIteration` direct agent call**

Build graph inputs:

```java
Map<String, Object> inputs = new HashMap<>();
inputs.put(CodeGenState.PROJECT_ID, String.valueOf(projectId));
inputs.put(CodeGenState.TASK_ID, taskId);
inputs.put(CodeGenState.ITERATION_PROMPT, prompt);
```

Stream the graph exactly like `runPipeline`.

- [ ] **Step 4: Run service tests and confirm GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=GenerationServiceIterationTest test
```

Expected: passes.

---

## Task 7: Frontend Minimal Chat Integration

**Files:**
- Modify: `lingmaForge-frontend/src/components/workbench/GenerationMode.vue`
- Modify: `lingmaForge-frontend/src/stores/workbench.ts`

- [ ] **Step 1: Add frontend state for iteration**

Track:

```ts
iterationTaskId: string | null
iterationRunning: boolean
iterationMessages: SSEMessage[]
```

- [ ] **Step 2: Submit chat prompt to backend**

Call:

```text
POST /api/generation/iterate
```

Payload:

```json
{
  "projectId": 123,
  "prompt": "把首页改成暗色科技风，并增加登录按钮"
}
```

- [ ] **Step 3: Subscribe to iteration SSE**

Open:

```text
/api/stream/iteration/{taskId}
```

Handle existing event names:

```text
message
file
log
complete
error
node_start
node_end
thinking
file_complete
```

- [ ] **Step 4: Refresh changed files**

When receiving `file` or `file_complete`, refresh the file tree and active editor content using the existing project file API. If the active editor path matches the changed file path, replace the editor content with the latest backend content.

- [ ] **Step 5: Run frontend checks**

Run:

```powershell
npm run type-check
```

Expected: passes.

---

## Task 8: End-To-End Verification

**Files:**
- Verification should not create planned feature files. If a verification command fails, modify the exact source or test file named in the failing output, then rerun the same command until it passes.

- [ ] **Step 1: Run targeted backend tests**

Run:

```powershell
.\mvnw.cmd -Dtest=CommonModelSerializationTest,CodeGenStateTest,IterationNodesTest,IterationPipelineTest,GenerationServiceIterationTest test
```

Expected: passes.

- [ ] **Step 2: Run backend test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: passes.

- [ ] **Step 3: Run frontend type check**

Run in `lingmaForge-frontend`:

```powershell
npm run type-check
```

Expected: passes.

- [ ] **Step 4: Manual smoke test**

Start backend and frontend, then verify:

1. Create a project from one sentence.
2. Open the IDE view.
3. Send a chat modification request.
4. See iteration SSE progress.
5. Confirm at least one file changes in the editor.
6. Confirm build result is streamed.
7. Confirm preview URL remains available or reports a clear error.

---

## Implementation Notes

- Keep every new public class and public method documented with Chinese Javadoc.
- Keep every state object serializable because LangGraph4j state cloning/persistence expects serializable state values.
- Prefer fixed graph flow for this phase. Add supervisor routing only after this pipeline is stable.
- Do not rewrite the frontend IDE layout in this phase; only wire the chat iteration flow.
- Avoid broad refactors in `GenerationService`; only extract helpers when a method becomes hard to read.
- Preserve existing REST paths so frontend changes stay small.

---

## Self-Review

- Spec coverage: The plan covers model/state, structured agent contract, iteration nodes, iteration graph, service wiring, frontend minimal integration, and verification.
- Placeholder scan: No task uses undefined future work as a completion requirement. Later-phase features are explicitly out of scope.
- Type consistency: New state constants, model names, node names, and service inputs are consistently named across tasks.
