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

import com.lingmaforge.backend.workbench.ai.node.BuildVerificationNode;
import com.lingmaforge.backend.workbench.ai.node.CodeGenerationNode;
import com.lingmaforge.backend.workbench.ai.node.PreviewDeployNode;
import com.lingmaforge.backend.workbench.ai.node.RequirementAnalysisNode;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.common.model.BuildStatus;

import jakarta.annotation.PostConstruct;

/**
 * 代码生成流水线——已收敛为 4 节点 agentic pipeline。
 *
 * <pre>START → requirement_analysis → code_generation → build_verification → preview_deploy → END
 *                               ↑ 构建失败回退重试（max 2）              ↓</pre>
 *
 * <p>原 6 节点中的 execution_planning + style_optimization 已删除：
 * <ul>
 *   <li>agentic code_generation 自己决定文件生成顺序，不再需要 Planner 预判 FilePlan</li>
 *   <li>agentic 工具循环中模型会写样式文件，不需要独立优化节点</li>
 * </ul>
 */
@Component
public class CodeGenPipeline {

    private static final Logger log = LoggerFactory.getLogger(CodeGenPipeline.class);

    public static final String ERROR_END = "error_end";

    private final RequirementAnalysisNode requirementAnalysisNode;
    private final CodeGenerationNode codeGenerationNode;
    private final BuildVerificationNode buildVerificationNode;
    private final PreviewDeployNode previewDeployNode;
    private final GenerationStreamRegistry streamRegistry;
    private final int maxRetryCount;

    private CompiledGraph<CodeGenState> compiledGraph;

    public CodeGenPipeline(RequirementAnalysisNode requirementAnalysisNode,
            CodeGenerationNode codeGenerationNode,
            BuildVerificationNode buildVerificationNode,
            PreviewDeployNode previewDeployNode,
            GenerationStreamRegistry streamRegistry,
            @Value("${lingma.pipeline.max-retry-count:2}") int maxRetryCount) {
        this.requirementAnalysisNode = requirementAnalysisNode;
        this.codeGenerationNode = codeGenerationNode;
        this.buildVerificationNode = buildVerificationNode;
        this.previewDeployNode = previewDeployNode;
        this.streamRegistry = streamRegistry;
        this.maxRetryCount = maxRetryCount;
    }

    @PostConstruct
    public void init() throws Exception {
        StateGraph<CodeGenState> graph = new StateGraph<>(CodeGenState.channels(), CodeGenState::new)
                .addNode(RequirementAnalysisNode.NODE_NAME, node_async(requirementAnalysisNode::execute))
                .addNode(CodeGenerationNode.NODE_NAME, node_async(codeGenerationNode::execute))
                .addNode(BuildVerificationNode.NODE_NAME, node_async(buildVerificationNode::execute))
                .addNode(PreviewDeployNode.NODE_NAME, node_async(previewDeployNode::execute))
                .addNode(ERROR_END, node_async(this::errorEnd));

        graph.addEdge(START, RequirementAnalysisNode.NODE_NAME);
        graph.addEdge(RequirementAnalysisNode.NODE_NAME, CodeGenerationNode.NODE_NAME);
        graph.addEdge(CodeGenerationNode.NODE_NAME, BuildVerificationNode.NODE_NAME);
        graph.addEdge(PreviewDeployNode.NODE_NAME, END);

        EdgeAction<CodeGenState> buildRouter = this::routeAfterBuild;
        graph.addConditionalEdges(BuildVerificationNode.NODE_NAME, edge_async(buildRouter),
                Map.of(
                        PreviewDeployNode.NODE_NAME, PreviewDeployNode.NODE_NAME,
                        CodeGenerationNode.NODE_NAME, CodeGenerationNode.NODE_NAME,
                        ERROR_END, ERROR_END));

        this.compiledGraph = graph.compile();
        log.info("代码生成 agentic pipeline 编译完成（4 节点），最大重试: {}", maxRetryCount);
    }

    public String routeAfterBuild(CodeGenState state) {
        BuildStatus status = state.buildStatus().orElse(BuildStatus.FAILED);
        if (status == BuildStatus.SUCCESS) return PreviewDeployNode.NODE_NAME;
        int retryCount = state.retryCount().orElse(0);
        if (retryCount > maxRetryCount) return ERROR_END;
        return CodeGenerationNode.NODE_NAME;
    }

    public Map<String, Object> errorEnd(CodeGenState state) {
        String taskId = state.taskId().orElse("");
        GenerationStreamEmitter emitter = streamRegistry.get(taskId);
        if (emitter != null) {
            emitter.error("生成失败：" + state.buildError().orElse("构建多次失败，已终止生成"));
        }
        return Map.of();
    }

    public CompiledGraph<CodeGenState> getCompiledGraph() {
        return compiledGraph;
    }
}