package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import com.lingmaforge.backend.workbench.ai.node.*;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("Pipeline Nodes")
@ExtendWith(MockitoExtension.class)
class PipelineNodesTest {

    private static final String TASK_ID = "test-task";
    private static final String PROJECT_ID = "1";

    @Mock private GenerationStreamRegistry streamRegistry;

    @BeforeEach
    void setUp() {
        lenient().when(streamRegistry.get(TASK_ID)).thenReturn(null);
    }

    private CodeGenState baseState() {
        Map<String, Object> data = new HashMap<>();
        data.put(CodeGenState.PROMPT, "创建一个电商应用");
        data.put(CodeGenState.PROJECT_ID, PROJECT_ID);
        data.put(CodeGenState.TASK_ID, TASK_ID);
        return new CodeGenState(data);
    }

    @Test
    @DisplayName("baseState contains required fields")
    void baseStateHasRequiredFields() {
        CodeGenState state = baseState();
        assertThat(state.prompt()).isPresent();
        assertThat(state.projectId()).isPresent();
        assertThat(state.taskId()).isPresent();
    }
}