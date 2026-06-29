package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lingmaforge.backend.model.BuildStatus;
import com.lingmaforge.backend.model.GeneratedFile;
import com.lingmaforge.backend.model.PlanResult;
import com.lingmaforge.backend.model.RequirementSpec;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CodeGenState（LangGraph4j AgentState）单元测试。
 *
 * <p>验证 Channel 的 LastValue 覆盖合并与 AppendingReducer 追加合并行为。</p>
 */
@DisplayName("CodeGenState 状态黑板测试")
class CodeGenStateTest {

    private static final Logger log = LoggerFactory.getLogger(CodeGenStateTest.class);
    private CodeGenState state;

    @BeforeEach
    void setUp() {
        Map<String, Object> initial = new HashMap<>();
        initial.put(CodeGenState.PROMPT, "测试需求");
        initial.put(CodeGenState.PROJECT_ID, "1");
        initial.put(CodeGenState.TASK_ID, "test-task-001");
        initial.put(CodeGenState.BUILD_STATUS, BuildStatus.PENDING);
        initial.put(CodeGenState.RETRY_COUNT, 0);
        state = new CodeGenState(initial);

        log.info("========== CodeGenState 初始化 ==========");
        log.info("  初始状态: PROMPT=测试需求, PROJECT_ID=1, TASK_ID=test-task-001");
        log.info("  BUILD_STATUS=PENDING, RETRY_COUNT=0, 共 {} 个 Channel", CodeGenState.channels().size());
        log.info("==========================================");
    }

    @Nested
    @DisplayName("基本字段读写")
    class BasicReadWrite {

        @Test
        @DisplayName("能正确读取初始值")
        void shouldReadInitialValues() {
            log.info("--- 基本字段读取测试 ---");
            log.info("  prompt={} (期望: '测试需求')", state.prompt().orElse(null));
            log.info("  projectId={} (期望: '1')", state.projectId().orElse(null));
            log.info("  taskId={} (期望: 'test-task-001')", state.taskId().orElse(null));
            log.info("  buildStatus={} (期望: PENDING)", state.buildStatus().orElse(null));
            log.info("  retryCount={} (期望: 0)", state.retryCount().orElse(null));

            assertThat(state.prompt()).hasValue("测试需求");
            assertThat(state.projectId()).hasValue("1");
            assertThat(state.taskId()).hasValue("test-task-001");
            assertThat(state.buildStatus()).hasValue(BuildStatus.PENDING);
            assertThat(state.retryCount()).hasValue(0);
            log.info("  [OK] 所有初始值读取正确");
        }

        @Test
        @DisplayName("未设置的字段返回 Optional.empty()")
        void shouldReturnEmptyForUnsetFields() {
            log.info("--- 未设置字段测试 ---");
            log.info("  analysisResult={} (期望: empty)", state.analysisResult());
            log.info("  planResult={} (期望: empty)", state.planResult());
            log.info("  previewUrl={} (期望: empty)", state.previewUrl());

            assertThat(state.analysisResult()).isEmpty();
            assertThat(state.planResult()).isEmpty();
            assertThat(state.previewUrl()).isEmpty();
            assertThat(state.buildError()).isEmpty();
            log.info("  [OK] 未设置字段均为 Optional.empty()");
        }
    }

    @Nested
    @DisplayName("LastValue 覆盖合并")
    class LastValueMerging {

        @Test
        @DisplayName("同一字段多次写入，后者覆盖前者")
        void shouldOverrideWithLastValue() {
            log.info("--- LastValue 覆盖语义测试 ---");
            log.info("  写入前 BUILD_STATUS: {}", state.buildStatus().orElse(null));

            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS),
                    CodeGenState.channels());
            state = new CodeGenState(data);

            log.info("  updateState(BUILD_STATUS=SUCCESS) -> 写入后: {}", state.buildStatus().orElse(null));
            assertThat(state.buildStatus()).hasValue(BuildStatus.SUCCESS);
            log.info("  [OK] LastValue 正确覆盖：新值替代了旧值");
        }

        @Test
        @DisplayName("retryCount 通过 LastValue 递增")
        void retryCountIncrements() {
            log.info("--- retryCount 递增测试 ---");

            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.RETRY_COUNT, 1), CodeGenState.channels());
            state = new CodeGenState(data);
            log.info("  第1次重试: RETRY_COUNT={}", state.retryCount().orElse(null));
            assertThat(state.retryCount()).hasValue(1);

            data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.RETRY_COUNT, 2), CodeGenState.channels());
            state = new CodeGenState(data);
            log.info("  第2次重试: RETRY_COUNT={}", state.retryCount().orElse(null));
            assertThat(state.retryCount()).hasValue(2);
            log.info("  [OK] retryCount 正确递增: 0 -> 1 -> 2");
        }

        @Test
        @DisplayName("构建失败状态写入")
        void buildStatusFromPendingToFailed() {
            log.info("--- 构建失败状态写入测试 ---");
            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                            CodeGenState.BUILD_ERROR, "TS2307: Cannot find module"),
                    CodeGenState.channels());
            state = new CodeGenState(data);

            log.info("  updateState(BUILD_STATUS=FAILED, BUILD_ERROR='TS2307: Cannot find module')");
            log.info("  写入后: BUILD_STATUS={}, BUILD_ERROR={}",
                    state.buildStatus().orElse(null), state.buildError().orElse(null));
            assertThat(state.buildStatus()).hasValue(BuildStatus.FAILED);
            assertThat(state.buildError()).hasValue("TS2307: Cannot find module");
            log.info("  [OK] 两个字段同时 LastValue 覆盖，均正确");
        }
    }

    @Nested
    @DisplayName("AppendingReducer 追加合并")
    class AppendingReducerMerging {

        @Test
        @DisplayName("多个节点追加 generatedFiles，列表累积不覆盖")
        void shouldAppendGeneratedFiles() {
            log.info("--- AppendingReducer 追加语义测试 ---");

            GeneratedFile f1 = new GeneratedFile("src/styles/globals.css", ":root {}", "abc", true);
            GeneratedFile f2 = new GeneratedFile("src/components/PlanCard.tsx",
                    "import React from 'react';", "def", true);

            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.GENERATED_FILES, new ArrayList<>(List.of(f1))),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            log.info("  追加文件1: src/styles/globals.css -> 累计 {} 个文件",
                    state.generatedFiles().map(List::size).orElse(0));

            data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.GENERATED_FILES, new ArrayList<>(List.of(f2))),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            log.info("  追加文件2: src/components/PlanCard.tsx -> 累计 {} 个文件",
                    state.generatedFiles().map(List::size).orElse(0));

            assertThat(state.generatedFiles()).isPresent();
            List<GeneratedFile> files = state.generatedFiles().get();
            assertThat(files).hasSize(2);
            assertThat(files.get(0).path()).isEqualTo("src/styles/globals.css");
            assertThat(files.get(1).path()).isEqualTo("src/components/PlanCard.tsx");
            log.info("  [OK] AppendingReducer 正确累积，两个写入合并为1个列表（共2个文件）");
        }
    }

    @Nested
    @DisplayName("完整流水线状态流转模拟")
    class FullPipelineFlow {

        @Test
        @DisplayName("模拟六个阶段的完整状态流转")
        void shouldSimulateFullPipeline() {
            log.info("========== 模拟6阶段完整流水线 ==========");
            Map<String, Object> data = state.data();

            RequirementSpec spec = new RequirementSpec("测试应用", "一个测试", List.of(), List.of(), List.of(), null);
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.ANALYSIS_RESULT, spec), CodeGenState.channels());
            log.info("  阶段1/6 需求分析 -> analysisResult 已写入 (appName='测试应用')");

            PlanResult plan = new PlanResult("react-vite-ts", "npm", List.of(), List.of(), List.of());
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.PLAN_RESULT, plan), CodeGenState.channels());
            log.info("  阶段2/6 执行规划 -> planResult 已写入 (framework=react-vite-ts)");

            GeneratedFile f1 = new GeneratedFile("src/App.tsx", "export default App;", "a1", true);
            GeneratedFile f2 = new GeneratedFile("src/styles/globals.css", ":root{}", "a2", true);
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.GENERATED_FILES, new ArrayList<>(List.of(f1))),
                    CodeGenState.channels());
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.GENERATED_FILES, new ArrayList<>(List.of(f2))),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            log.info("  阶段3/6 代码生成 -> generatedFiles +1 (src/App.tsx)");
            log.info("  阶段4/6 样式优化 -> generatedFiles +1 (src/styles/globals.css)");

            data = AgentState.updateState(data,
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS,
                            CodeGenState.BUILD_TIME, 4), CodeGenState.channels());
            log.info("  阶段5/6 构建验证 -> BUILD_STATUS=SUCCESS, BUILD_TIME=4s");

            data = AgentState.updateState(data,
                    Map.of(CodeGenState.PREVIEW_URL, "https://sandbox.local/1",
                            CodeGenState.PREVIEW_PORT, 5173), CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.previewUrl()).hasValue("https://sandbox.local/1");
            log.info("  阶段6/6 预览部署 -> PREVIEW_URL=https://sandbox.local/1, PREVIEW_PORT=5173");
            log.info("[OK] 6阶段完整流转模拟通过");
        }

        @Test
        @DisplayName("模拟构建失败 -> 回退 -> 最终成功")
        void shouldSimulateRetryFlow() {
            log.info("========== 模拟构建失败+重试流程 ==========");
            Map<String, Object> data = state.data();

            data = AgentState.updateState(data,
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                            CodeGenState.BUILD_ERROR, "TS2307: ./PlanCard not found",
                            CodeGenState.RETRY_COUNT, 1), CodeGenState.channels());
            state = new CodeGenState(data);
            log.info("  重试1/2 -> BUILD_STATUS=FAILED, error='TS2307: ./PlanCard not found'");

            data = AgentState.updateState(data,
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS,
                            CodeGenState.BUILD_TIME, 7), CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.buildStatus()).hasValue(BuildStatus.SUCCESS);
            log.info("  重试2/2 -> BUILD_STATUS=SUCCESS, BUILD_TIME=7s");
            log.info("[OK] 构建失败 -> 回退修复 -> 最终成功 (耗时7秒)");
        }
    }
}
