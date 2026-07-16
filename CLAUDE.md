# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

灵码工坊 (LingmaForge) — AI-powered "conversation-to-application" platform. Users describe an app in natural language and the platform generates a complete, runnable project. Monorepo with frontend and backend sub-projects.

## Essential Commands

### Frontend (`lingmaForge-frontend/`)

Requires Node `^22.18.0 || >=24.12.0`.

```sh
npm install          # install dependencies
npm run dev          # Vite dev server with HMR (proxies /api → localhost:8081)
npm run build        # type-check + production build
npm run lint         # oxlint + eslint (auto-fix)
npm test             # run generationCore unit tests (Node native test runner)
npm run type-check   # vue-tsc type checking only
```

### Backend (`lingmaForge-backend/`)

Requires JDK 21, Maven 3.9+. Uses Maven wrapper (`./mvnw`).

```sh
./mvnw spring-boot:run   # start on port 8081 (H2 embedded DB, works out of box)
./mvnw test               # run JUnit 5 tests
```

API keys are sourced from environment variables (e.g. `DEEPSEEK_API_KEY`). Without them, the backend starts but AI generation is unavailable. See `application-dev.yml` for all model/agent configuration.

## Architecture

### Communication Flow

```
Browser (Vue 3) ──REST (fetch)──▶ Spring Boot (8081)
                 ──SSE (EventSource)──▶ /api/stream/generation/{taskId}
                                          /api/stream/iteration/{taskId}
```

The Vite dev server proxies `/api` → `http://localhost:8081`. The frontend uses a custom `request()` wrapper (`src/api/request.ts`) that handles Snowflake ID precision loss (16+ digit integers → strings) and unwraps the backend's `Result<T>` envelope.

### Pure Functional Core (Frontend)

`src/core/generationCore.mjs` is a **framework-independent state machine** for the generation pipeline. It defines all types (`PipelineNodeName`, `SSEMessage`, `WorkbenchCoreState`) and exports pure reducer functions (`reduceGenerationMessage`, `reduceGenerationComplete`, `reduceGenerationError`) that operate on immutable state via `cloneState()`. The Pinia workbench store (`src/stores/workbench.ts`, ~647 lines) wraps these pure functions, adding side effects: API calls, SSE connections, and reactive state exposure.

### Workbench Mode State Machine

```
simple ──(user submits prompt)──▶ generation ──(pipeline completes)──▶ complete
   ▲                                                                      │
   └────────────────────(reset)───────────────────────────────────────────┘
```

- `simple` — centered input form (`SimpleMode.vue`)
- `generation` — 4-column IDE layout with real-time streaming (`GenerationMode.vue`)
- `complete` — results view with editor + preview

### Backend AI Pipeline (LangGraph4j StateGraph)

6-node sequential pipeline in `CodeGenPipeline.java`:

```
START → requirement_analysis → execution_planning → code_generation
       → style_optimization → build_verification → preview_deploy → END
```

- **Conditional retry**: If `build_verification` fails, routes back to `code_generation` (max 2 retries) with build errors injected into the prompt
- **Nodes** are Spring `@Component`s that delegate to LangChain4j `AiServices` agents
- **State** (`CodeGenState`) uses LangGraph4j `AgentState` with channel-based merge strategies (`LastValue` for scalars, `Appender` for file lists)

### Tool-Use Architecture (Backend)

LangChain4j `@Tool` methods (in `FileTools`, `ProjectContextTools`, `IterationTools`) need runtime context (projectId, taskId, SSE emitter). Two patterns bridge the gap:

1. **`GenerationContext`** — `ThreadLocal` holding context, set by `AbstractCodeGenNode.setupContext()` before each node runs, cleared in `finally`
2. **`GenerationStreamRegistry`** — `ConcurrentHashMap<taskId, emitter>` for fork-join threads that can't inherit ThreadLocal

Agents are created via `AgentFactory` which routes each stage to the configured LLM model and loads prompt templates from `src/main/resources/prompts/`.

### Dual-Write File Pattern

Generated files are written to **both** the database (`lf_project_file` table via MyBatis-Plus) and the **filesystem** (`./workspace/{projectId}/`). The DB copy serves the frontend (file tree, editor) while the filesystem copy enables `npm install && npm run build` in the sandbox.

### SSE Event Types

Emitted from `GenerationService` through `SseEmitter`:

| Event | Data | When |
|-------|------|------|
| `message` | threadId, nodeName, text, textType | Pipeline node progress |
| `file` | path, content, status | AI writes a file via `@Tool writeFile` |
| `log` | text | Build logs (npm install/build) |
| `complete` | url, port, buildTime | Pipeline finished |
| `error` | nodeName, text | Any failure |

## Key Config Files

| File | Purpose |
|------|---------|
| `lingmaForge-frontend/vite.config.ts` | Dev proxy to `localhost:8081`, `@` path alias |
| `lingmaForge-backend/src/main/resources/application-dev.yml` | All backend config: DB, LLM models, agent routing, sandbox, pipeline settings |
| `lingmaForge-backend/src/main/resources/application-local.yml` | API keys (gitignored, same structure as dev) |

## Tech Stack

- **Frontend**: Vue 3 (Composition API + `<script setup>`), TypeScript 6, Vite 8, Pinia 3, Vue Router 5, custom CSS (no UI framework), SVG sprite icons
- **Backend**: Spring Boot 3.5.7, Java 21, MyBatis-Plus 3.5, LangChain4j 1.16 + LangGraph4j 1.8, H2 (dev) / MySQL 8 (prod)
- **AI Models**: DeepSeek V4 Flash/Pro (primary), Qwen3-Coder-Plus (backup), Moonshot Kimi K2.7 (premium), Anthropic Claude (adapter present)
- **Auth**: Not yet implemented — all endpoints are open
