package com.lingmaforge.backend.ai.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lingmaforge.backend.common.model.BuildResult;
import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.common.model.ProjectContext;
import com.lingmaforge.backend.workbench.ai.dialog.DelegateIterateNode;
import com.lingmaforge.backend.workbench.ai.dialog.DialogState;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;
import com.lingmaforge.backend.workbench.service.ProjectService;
import com.lingmaforge.backend.workbench.service.SandboxService;

/**
 * DelegateIterateNode 单元测试。
 *
 * <p>覆盖入口校验（projectId/emitter/停止）、modify 成功 + 构建成功/失败、
 * modify 异常、GenerationContext 清理。</p>
 */
@DisplayName("DelegateIterateNode 单元测试")
@ExtendWith(MockitoExtension.class)
class DelegateIterateNodeTest {

    @Mock private AgentFactory agentFactory;
    @Mock private GenerationStreamRegistry streamRegistry;
    @Mock private SandboxService sandboxService;
    @Mock private ProjectService projectService;
    @Mock private GenerationStreamEmitter emitter;
    @Mock private IterationAgent iterationAgent;

    private DelegateIterateNode node;

    @BeforeEach
    void setUp() {
        lenient().when(agentFactory.createIterationAgent()).thenReturn(iterationAgent);
        lenient().when(streamRegistry.get(anyString())).thenReturn(emitter);
        lenient().when(streamRegistry.isStopRequested(anyString())).thenReturn(false);
        lenient().when(iterationAgent.modify(anyString())).thenReturn("修改完成");
        lenient().when(projectService.getProjectContext(anyLong()))
                .thenReturn(new ProjectContext("vue", List.of("src/App.vue"), List.of()));
        node = new DelegateIterateNode(agentFactory, streamRegistry, sandboxService, projectService);
    }

    private DialogState stateWith(String dialogId, String projectId, String userMessage) {
        Map<String, Object> data = new HashMap<>();
        if (dialogId != null) {
            data.put(DialogState.DIALOG_ID, dialogId);
        }
        if (projectId != null) {
            data.put(DialogState.PROJECT_ID, projectId);
        }
        if (userMessage != null) {
            data.put(DialogState.USER_MESSAGE, userMessage);
        }
        return new DialogState(data);
    }

    @Test
    @DisplayName("projectId 缺失 → 返回失败摘要，不调 modify")
    void shouldFailWhenProjectIdMissing() {
        DialogState state = stateWith("d1", null, "改成蓝色");
        Map<String, Object> result = node.execute(state);
        assertThat(result.get(DialogState.DELEGATE_RESULT))
                .asString().contains("未关联项目");
        org.mockito.Mockito.verifyNoInteractions(iterationAgent, sandboxService);
    }

    @Test
    @DisplayName("emitter 缺失 → 返回失败摘要，不调 modify")
    void shouldFailWhenEmitterMissing() {
        when(streamRegistry.get("d1")).thenReturn(null);
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        assertThat(result.get(DialogState.DELEGATE_RESULT))
                .asString().contains("SSE 连接未建立");
        org.mockito.Mockito.verifyNoInteractions(iterationAgent, sandboxService);
    }

    @Test
    @DisplayName("已请求停止 → 返回已取消摘要，不调 modify")
    void shouldReturnCancelledWhenStopRequested() {
        when(streamRegistry.isStopRequested("d1")).thenReturn(true);
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        assertThat(result.get(DialogState.DELEGATE_RESULT))
                .asString().contains("已取消");
        org.mockito.Mockito.verifyNoInteractions(iterationAgent, sandboxService);
    }

    @Test
    @DisplayName("modify 成功 + 构建成功 → 摘要含修改完成与构建通过，推 complete")
    void shouldSucceedWhenBuildPasses() {
        when(sandboxService.npmBuild(100L))
                .thenReturn(new BuildResult(BuildStatus.SUCCESS, "ok", null, 3000L));
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        String summary = (String) result.get(DialogState.DELEGATE_RESULT);
        assertThat(summary).contains("修改完成").contains("构建通过");
        verify(emitter).complete("", 0, 3);
    }

    @Test
    @DisplayName("modify 成功 + 构建失败 → 摘要含构建失败，推 error")
    void shouldReportErrorWhenBuildFails() {
        when(sandboxService.npmBuild(100L))
                .thenReturn(new BuildResult(BuildStatus.FAILED, "out", "SyntaxError", 1000L));
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        String summary = (String) result.get(DialogState.DELEGATE_RESULT);
        assertThat(summary).contains("构建失败").contains("修改完成");
        verify(emitter).error(org.mockito.ArgumentMatchers.contains("构建失败"));
    }

    @Test
    @DisplayName("modify 抛异常 → 返回失败摘要，推 error")
    void shouldFailWhenModifyThrows() {
        when(iterationAgent.modify(anyString()))
                .thenThrow(new IllegalStateException("模型不可用"));
        DialogState state = stateWith("d1", "100", "改成蓝色");
        Map<String, Object> result = node.execute(state);
        assertThat(result.get(DialogState.DELEGATE_RESULT))
                .asString().contains("代码修改失败");
        verify(emitter).error(org.mockito.ArgumentMatchers.contains("代码修改失败"));
    }

    @Test
    @DisplayName("modify 异常后 GenerationContext 已清理")
    void shouldClearContextAfterModifyFailure() {
        when(iterationAgent.modify(anyString()))
                .thenThrow(new IllegalStateException("模型不可用"));
        DialogState state = stateWith("d1", "100", "改成蓝色");
        node.execute(state);
        // GenerationContext.get() 在 clear 后应抛 IllegalStateException
        assertThatThrownBy(GenerationContext::get)
                .isInstanceOf(IllegalStateException.class);
    }
}
