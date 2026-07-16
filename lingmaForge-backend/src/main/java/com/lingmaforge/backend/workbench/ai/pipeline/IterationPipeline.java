package com.lingmaforge.backend.workbench.ai.pipeline;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.Map;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.workbench.ai.node.BuildVerificationNode;
import com.lingmaforge.backend.workbench.ai.node.CodePatchNode;
import com.lingmaforge.backend.workbench.ai.node.PreviewDeployNode;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;

import jakarta.annotation.PostConstruct;

/**
 * 对话式迭代 agentic pipeline——<b>已从 7 节点 DAG 收敛为 4 节点</b>。
 *
 * <p>旧版流程（7 节点，已废弃）：
 * <pre>START → intent_analysis → context_load → modification_planning → code_patch → build → preview</pre>
 * 新版流程（4 节点，当前生效）：
 * <pre>START → code_patch → build_verification → preview_deploy → END
 *                                     ↑               ↓ (success)
 *                                     └── (fail→retry)  ──┘  (fail→maxRetry) → error_end</pre>
 * 其中 {@code code_patch} 节点已收敛为单一 agentic 节点，内联了上下文装载
 * 与指令理解逻辑，模型自主 read → write → updatePlan → exit 完成修改。</p>
 *
 * <p>构建失败重试：{@code BuildVerificationNode} 写入 {@code BUILD_ERROR} 后
 * 直接回退到 {@code code_patch}（而非走独立的 error_analysis→context_load→planning 链），
 * 由 agent 在 prompt 中看到构建错误后定向修复。retry 门限 {@code retryCount > maxRetryCount}
 * 时落入 {@code error_end} 终止。</p>
 */
@Component
public class IterationPipeline {

    private static final Logger log = LoggerFactory.getLogger(IterationPipeline.class);

    public static final String ERROR_END = "iteration_error_end";

    private final CodePatchNode codePatchNode;
    private final BuildVerificationNode buildVerificationNode;
    private final PreviewDeployNode previewDeployNode;
    private final GenerationStreamRegistry streamRegistry;
    private final int maxRetryCount;

    private CompiledGraph<CodeGenState> compiledGraph;

    public IterationPipeline(
            CodePatchNode codePatchNode,
            BuildVerificationNode buildVerificationNode,
            PreviewDeployNode previewDeployNode,
            GenerationStreamRegistry streamRegistry,
            @Value("${lingma.pipeline.max-retry-count:2}") int maxRetryCount) {
        this.codePatchNode = codePatchNode;
        this.buildVerificationNode = buildVerificationNode;
        this.previewDeployNode = previewDeployNode;
        this.streamRegistry = streamRegistry;
        this.maxRetryCount = maxRetryCount;
    }

    @PostConstruct
    public void init() throws Exception {
        StateGraph<CodeGenState> graph = new StateGraph<>(CodeGenState.channels(), CodeGenState::new)
                .addNode(CodePatchNode.NODE_NAME, node_async(codePatchNode::execute))
                .addNode(BuildVerificationNode.NODE_NAME, node_async(buildVerificationNode::execute))
                .addNode(PreviewDeployNode.NODE_NAME, node_async(previewDeployNode::execute))
                .addNode(ERROR_END, node_async(this::errorEnd));

        graph.addEdge(START, CodePatchNode.NODE_NAME);
        graph.addEdge(CodePatchNode.NODE_NAME, BuildVerificationNode.NODE_NAME);
        graph.addEdge(PreviewDeployNode.NODE_NAME, END);

        EdgeAction<CodeGenState> buildRouter = this::routeAfterBuild;
        graph.addConditionalEdges(BuildVerificationNode.NODE_NAME, edge_async(buildRouter),
                Map.of(
                        PreviewDeployNode.NODE_NAME, PreviewDeployNode.NODE_NAME,
                        CodePatchNode.NODE_NAME, CodePatchNode.NODE_NAME,
                        ERROR_END, ERROR_END));

        this.compiledGraph = graph.compile();
        log.info("对话式迭代 agentic pipeline 编译完成（4 节点），最大重试次数: {}", maxRetryCount);
    }

    /**
     * 构建失败路由：成功 → preview；失败且 retries 未超限 → 回退到 code_patch（agent 看错误后自修复）；
     * retries 超限 → error_end。
     */
    public String routeAfterBuild(CodeGenState state) {
        BuildStatus status = state.buildStatus().orElse(BuildStatus.FAILED);
        if (status == BuildStatus.SUCCESS) {
            return PreviewDeployNode.NODE_NAME;
        }
        int retryCount = state.retryCount().orElse(0);
        if (retryCount > maxRetryCount) {
            return ERROR_END;
        }
        return CodePatchNode.NODE_NAME;
    }

    public Map<String, Object> errorEnd(CodeGenState state) {
        String taskId = state.taskId().orElse("");
        GenerationStreamEmitter emitter = streamRegistry.get(taskId);
        if (emitter != null) {
            String error = state.buildError().orElse("构建多次失败，已终止本轮对话式修改");
            emitter.error("迭代修改失败：" + error);
        }
        return Map.of();
    }

    public CompiledGraph<CodeGenState> getCompiledGraph() {
        return compiledGraph;
    }
}