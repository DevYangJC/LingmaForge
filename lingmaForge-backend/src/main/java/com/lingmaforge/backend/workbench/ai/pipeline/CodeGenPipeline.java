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
import com.lingmaforge.backend.workbench.ai.node.ExecutionPlanningNode;
import com.lingmaforge.backend.workbench.ai.node.PreviewDeployNode;
import com.lingmaforge.backend.workbench.ai.node.RequirementAnalysisNode;
import com.lingmaforge.backend.workbench.ai.node.StyleOptimizationNode;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.common.model.BuildStatus;

import jakarta.annotation.PostConstruct;

/**
 * 代码生成流水线的 StateGraph 编排。
 *
 * <p>六节点：需求分析 → 执行规划 → 代码生成 → 样式优化 → 构建验证 → 预览部署。
 * StateGraph 是"导演"，定义节奏与条件分支；各节点内的 Agent 是"演员"，自主决策具体内容。</p>
 *
 * <p>关键设计：构建验证失败时通过条件边回退到代码生成节点，由 Agent 修复代码后重新构建，
 * 回退次数超过上限（默认 2 次）则进入 error_end 终止节点，避免无限循环。</p>
 */
@Component
public class CodeGenPipeline {

    private static final Logger log = LoggerFactory.getLogger(CodeGenPipeline.class);

    /** 回退次数达到上限后的终止节点名。 */
    public static final String ERROR_END = "error_end";

    private final RequirementAnalysisNode requirementAnalysisNode;
    private final ExecutionPlanningNode executionPlanningNode;
    private final CodeGenerationNode codeGenerationNode;
    private final StyleOptimizationNode styleOptimizationNode;
    private final BuildVerificationNode buildVerificationNode;
    private final PreviewDeployNode previewDeployNode;
    private final GenerationStreamRegistry streamRegistry;
    private final int maxRetryCount;

    private CompiledGraph<CodeGenState> compiledGraph;

    public CodeGenPipeline(RequirementAnalysisNode requirementAnalysisNode,
            ExecutionPlanningNode executionPlanningNode,
            CodeGenerationNode codeGenerationNode,
            StyleOptimizationNode styleOptimizationNode,
            BuildVerificationNode buildVerificationNode,
            PreviewDeployNode previewDeployNode,
            GenerationStreamRegistry streamRegistry,
            @Value("${lingma.pipeline.max-retry-count:2}") int maxRetryCount) {
        this.requirementAnalysisNode = requirementAnalysisNode;
        this.executionPlanningNode = executionPlanningNode;
        this.codeGenerationNode = codeGenerationNode;
        this.styleOptimizationNode = styleOptimizationNode;
        this.buildVerificationNode = buildVerificationNode;
        this.previewDeployNode = previewDeployNode;
        this.streamRegistry = streamRegistry;
        this.maxRetryCount = maxRetryCount;
    }

    /**
     * 构建并编译 StateGraph。
     */
    @PostConstruct
    public void init() throws Exception {
        StateGraph<CodeGenState> graph = new StateGraph<>(CodeGenState.channels(), CodeGenState::new)
                .addNode(RequirementAnalysisNode.NODE_NAME, node_async(requirementAnalysisNode::execute))
                .addNode(ExecutionPlanningNode.NODE_NAME, node_async(executionPlanningNode::execute))
                .addNode(CodeGenerationNode.NODE_NAME, node_async(codeGenerationNode::execute))
                .addNode(StyleOptimizationNode.NODE_NAME, node_async(styleOptimizationNode::execute))
                .addNode(BuildVerificationNode.NODE_NAME, node_async(buildVerificationNode::execute))
                .addNode(PreviewDeployNode.NODE_NAME, node_async(previewDeployNode::execute))
                .addNode(ERROR_END, node_async(this::errorEnd));

        graph.addEdge(START, RequirementAnalysisNode.NODE_NAME);
        graph.addEdge(RequirementAnalysisNode.NODE_NAME, ExecutionPlanningNode.NODE_NAME);
        graph.addEdge(ExecutionPlanningNode.NODE_NAME, CodeGenerationNode.NODE_NAME);
        graph.addEdge(CodeGenerationNode.NODE_NAME, StyleOptimizationNode.NODE_NAME);
        graph.addEdge(StyleOptimizationNode.NODE_NAME, BuildVerificationNode.NODE_NAME);
        graph.addEdge(PreviewDeployNode.NODE_NAME, END);

        // 构建验证条件边：成功 → 预览部署；失败且未超上限 → 回退代码生成；超上限 → 终止
        EdgeAction<CodeGenState> buildRouter = this::routeAfterBuild;
        graph.addConditionalEdges(BuildVerificationNode.NODE_NAME, edge_async(buildRouter),
                Map.of(
                        PreviewDeployNode.NODE_NAME, PreviewDeployNode.NODE_NAME,
                        CodeGenerationNode.NODE_NAME, CodeGenerationNode.NODE_NAME,
                        ERROR_END, ERROR_END));

        this.compiledGraph = graph.compile();
        log.info("代码生成流水线 StateGraph 编译完成，最大回退次数: {}", maxRetryCount);
    }

    /**
     * 构建验证后的路由决策。
     *
     * @param state 流水线状态
     * @return 下一个节点名
     */
    // public 可见性供构建验证测试直接调用条件路由逻辑
    public String routeAfterBuild(CodeGenState state) {
        BuildStatus status = state.buildStatus().orElse(BuildStatus.FAILED);
        if (status == BuildStatus.SUCCESS) {
            return PreviewDeployNode.NODE_NAME;
        }
        int retryCount = state.retryCount().orElse(0);
        if (retryCount > maxRetryCount) {
            return ERROR_END;
        }
        return CodeGenerationNode.NODE_NAME;
    }

    /**
     * 终止节点：推送错误事件。
     *
     * @param state 流水线状态
     * @return 空状态更新
     */
    public Map<String, Object> errorEnd(CodeGenState state) {
        String taskId = state.taskId().orElse("");
        GenerationStreamEmitter emitter = streamRegistry.get(taskId);
        if (emitter != null) {
            String error = state.buildError().orElse("构建多次失败，已终止生成");
            emitter.error("生成失败：" + error);
        }
        return Map.of();
    }

    /**
     * 获取编译后的图实例。
     *
     * @return CompiledGraph
     */
    public CompiledGraph<CodeGenState> getCompiledGraph() {
        return compiledGraph;
    }
}
