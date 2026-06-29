package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.lingmaforge.backend.model.BuildStatus;
import com.lingmaforge.backend.model.GeneratedFile;
import com.lingmaforge.backend.model.PlanResult;
import com.lingmaforge.backend.model.RequirementSpec;

/**
 * CodeGenState（LangGraph4j AgentState）单元测试。
 *
 * <p>验证 Channel 的 LastValue 覆盖合并与 AppendingReducer 追加合并行为。
 * 使用 {@link AgentState#updateState} 静态方法模拟图节点的状态流转。</p>
 */
@DisplayName("CodeGenState 状态黑板测试")
class CodeGenStateTest {

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
    }

    // ==================== 基本读取 ====================

    @Nested
    @DisplayName("基本字段读写")
    class BasicReadWrite {

        @Test
        @DisplayName("能正确读取初始值")
        void shouldReadInitialValues() {
            assertThat(state.prompt()).hasValue("测试需求");
            assertThat(state.projectId()).hasValue("1");
            assertThat(state.taskId()).hasValue("test-task-001");
            assertThat(state.buildStatus()).hasValue(BuildStatus.PENDING);
            assertThat(state.retryCount()).hasValue(0);
        }

        @Test
        @DisplayName("未设置的字段返回 Optional.empty()")
        void shouldReturnEmptyForUnsetFields() {
            assertThat(state.analysisResult()).isEmpty();
            assertThat(state.planResult()).isEmpty();
            assertThat(state.previewUrl()).isEmpty();
            assertThat(state.buildError()).isEmpty();
        }

        @Test
        @DisplayName("手动构造的 state，未设置字段返回 empty(Channel 默认值由 StateGraph.compile 时填充)")
        void shouldReturnEmptyForManuallyConstructedState() {
            // 手动 new CodeGenState(Map) 不会自动应用 Channel 默认值；
            // 默认值只在 StateGraph.compile 初始化时填充。
            assertThat(state.currentFileIndex()).isEmpty();
            assertThat(state.buildTime()).isEmpty();
            assertThat(state.previewPort()).isEmpty();
        }
    }

    // ==================== LastValue 覆盖合并 ====================

    @Nested
    @DisplayName("LastValue 覆盖合并")
    class LastValueMerging {

        @Test
        @DisplayName("同一字段多次写入，后者覆盖前者")
        void shouldOverrideWithLastValue() {
            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.buildStatus()).hasValue(BuildStatus.SUCCESS);
        }

        @Test
        @DisplayName("buildStatus 从 PENDING 变为 SUCCESS")
        void buildStatusFromPendingToSuccess() {
            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.buildStatus()).hasValue(BuildStatus.SUCCESS);
        }

        @Test
        @DisplayName("buildStatus 从 PENDING 变为 FAILED，写错误信息")
        void buildStatusFromPendingToFailed() {
            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                            CodeGenState.BUILD_ERROR, "TS2307: Cannot find module"),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.buildStatus()).hasValue(BuildStatus.FAILED);
            assertThat(state.buildError()).hasValue("TS2307: Cannot find module");
        }

        @Test
        @DisplayName("retryCount 通过 LastValue 递增")
        void retryCountIncrements() {
            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.RETRY_COUNT, 1),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.retryCount()).hasValue(1);

            data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.RETRY_COUNT, 2),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.retryCount()).hasValue(2);
        }
    }

    // ==================== AppendingReducer 追加合并 ====================

    @Nested
    @DisplayName("AppendingReducer 追加合并")
    class AppendingReducerMerging {

        @Test
        @DisplayName("多个节点追加 generatedFiles，列表累积不覆盖")
        void shouldAppendGeneratedFiles() {
            // 使用可变的 ArrayList 作为初始值
            GeneratedFile file1 = new GeneratedFile("src/styles/globals.css", ":root {}", "abc", true);
            GeneratedFile file2 = new GeneratedFile("src/components/PlanCard.tsx",
                    "import React from 'react';", "def", true);

            var data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.GENERATED_FILES, new ArrayList<>(List.of(file1))),
                    CodeGenState.channels());
            state = new CodeGenState(data);

            data = AgentState.updateState(state.data(),
                    Map.of(CodeGenState.GENERATED_FILES, new ArrayList<>(List.of(file2))),
                    CodeGenState.channels());
            state = new CodeGenState(data);

            assertThat(state.generatedFiles()).isPresent();
            List<GeneratedFile> files = state.generatedFiles().get();
            assertThat(files).hasSize(2);
            assertThat(files.get(0).path()).isEqualTo("src/styles/globals.css");
            assertThat(files.get(1).path()).isEqualTo("src/components/PlanCard.tsx");
        }
    }

    // ==================== 完整流水线状态流转 ====================

    @Nested
    @DisplayName("完整流水线状态流转模拟")
    class FullPipelineFlow {

        @Test
        @DisplayName("模拟六个阶段的完整状态流转")
        void shouldSimulateFullPipeline() {
            Map<String, Object> data = state.data();

            // 阶段 1: 需求分析 → 写入 analysisResult
            RequirementSpec spec = new RequirementSpec(
                    "测试应用", "一个测试", List.of(), List.of(), List.of(), null);
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.ANALYSIS_RESULT, spec),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.analysisResult()).isPresent();

            // 阶段 2: 执行规划 → 写入 planResult
            PlanResult plan = new PlanResult("react-vite-ts", "npm", List.of(), List.of(), List.of());
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.PLAN_RESULT, plan),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.planResult()).isPresent();

            // 阶段 3+4: 代码生成 + 样式优化 → 追加 generatedFiles
            GeneratedFile f1 = new GeneratedFile("src/App.tsx", "export default App;", "a1", true);
            GeneratedFile f2 = new GeneratedFile("src/styles/globals.css", ":root{}", "a2", true);
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.GENERATED_FILES, new ArrayList<>(List.of(f1))),
                    CodeGenState.channels());
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.GENERATED_FILES, new ArrayList<>(List.of(f2))),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.generatedFiles().get()).hasSize(2);

            // 阶段 5: 构建验证成功
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS,
                            CodeGenState.BUILD_TIME, 4),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.buildStatus()).hasValue(BuildStatus.SUCCESS);

            // 阶段 6: 预览部署
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.PREVIEW_URL, "https://sandbox.local/1",
                            CodeGenState.PREVIEW_PORT, 5173),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.previewUrl()).hasValue("https://sandbox.local/1");
        }

        @Test
        @DisplayName("模拟构建失败 → 回退 → 最终成功的状态流转")
        void shouldSimulateRetryFlow() {
            Map<String, Object> data = state.data();

            // 第 1 次构建失败
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.FAILED,
                            CodeGenState.BUILD_ERROR, "TS2307: ./PlanCard not found",
                            CodeGenState.RETRY_COUNT, 1),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.buildStatus()).hasValue(BuildStatus.FAILED);

            // 代码生成读取 buildError 修复 → 重新构建成功
            data = AgentState.updateState(data,
                    Map.of(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS,
                            CodeGenState.BUILD_TIME, 7),
                    CodeGenState.channels());
            state = new CodeGenState(data);
            assertThat(state.buildStatus()).hasValue(BuildStatus.SUCCESS);
        }
    }
}
