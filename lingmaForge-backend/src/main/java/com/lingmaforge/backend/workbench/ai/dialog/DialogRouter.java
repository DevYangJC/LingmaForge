package com.lingmaforge.backend.workbench.ai.dialog;

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
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 对话入口层 StateGraph 编排。
 *
 * <p>四节点：意图识别 → 条件边 → (delegate_codegen | delegate_iterate | chat_reply) → END。
 * StateGraph 是"导演"，第一个节点识别意图，条件边据此路由到三个 delegate 子图。</p>
 *
 * <p>Phase 1 三个 delegate 均为桩节点；Phase 2-4 逐步填充真实逻辑：
 * <ul>
 *   <li>Phase 2：chat_reply 接 StreamingChatModel + SSE 流式回复</li>
 *   <li>Phase 3：delegate_iterate 桥接 IterationAgent 增量补丁</li>
 *   <li>Phase 4：delegate_codegen 桥接 CodeGenPipeline 全量生成</li>
 * </ul></p>
 *
 * <p>新旧接口并存：{@code /api/generation/*} 保留直跑流水线；{@code /api/chat/*} 走本对话入口。</p>
 *
 * <p><b>调用方注记</b>：Phase 2 的 ChatController 应通过
 * {@code getCompiledGraph().stream(inputs)} 驱动本图，逐节点消费 {@code NodeOutput}
 * 并把 delegate 节点的结果经 SSE 推给前端；不要直接调用各节点 execute 方法。</p>
 */
@Component
public class DialogRouter {

    private static final Logger log = LoggerFactory.getLogger(DialogRouter.class);

    private final IntentDetectionNode intentDetectionNode;
    private final DelegateCodegenNode delegateCodegenNode;
    private final DelegateIterateNode delegateIterateNode;
    private final ChatReplyNode chatReplyNode;

    private CompiledGraph<DialogState> compiledGraph;

    public DialogRouter(IntentDetectionNode intentDetectionNode,
            DelegateCodegenNode delegateCodegenNode,
            DelegateIterateNode delegateIterateNode,
            ChatReplyNode chatReplyNode) {
        this.intentDetectionNode = intentDetectionNode;
        this.delegateCodegenNode = delegateCodegenNode;
        this.delegateIterateNode = delegateIterateNode;
        this.chatReplyNode = chatReplyNode;
    }

    /**
     * 构建并编译对话 StateGraph。
     */
    @PostConstruct
    public void init() throws Exception {
        StateGraph<DialogState> graph = new StateGraph<>(DialogState.channels(), DialogState::new)
                .addNode(IntentDetectionNode.NODE_NAME, node_async(intentDetectionNode::execute))
                .addNode(DelegateCodegenNode.NODE_NAME, node_async(delegateCodegenNode::execute))
                .addNode(DelegateIterateNode.NODE_NAME, node_async(delegateIterateNode::execute))
                .addNode(ChatReplyNode.NODE_NAME, node_async(chatReplyNode::execute));

        graph.addEdge(START, IntentDetectionNode.NODE_NAME);

        // 意图识别后的条件边：按意图路由到对应 delegate 节点
        EdgeAction<DialogState> intentRouter = this::routeByIntent;
        graph.addConditionalEdges(IntentDetectionNode.NODE_NAME, edge_async(intentRouter),
                Map.of(
                        DelegateCodegenNode.NODE_NAME, DelegateCodegenNode.NODE_NAME,
                        DelegateIterateNode.NODE_NAME, DelegateIterateNode.NODE_NAME,
                        ChatReplyNode.NODE_NAME, ChatReplyNode.NODE_NAME));

        graph.addEdge(DelegateCodegenNode.NODE_NAME, END);
        graph.addEdge(DelegateIterateNode.NODE_NAME, END);
        graph.addEdge(ChatReplyNode.NODE_NAME, END);

        this.compiledGraph = graph.compile();
        log.info("对话入口层 DialogRouter StateGraph 编译完成");
    }

    /**
     * 意图识别后的路由决策。
     *
     * @param state 对话状态
     * @return 下一个节点名
     */
    // public 可见性供单测直接调用条件路由逻辑
    public String routeByIntent(DialogState state) {
        DialogIntent intent = state.intent().orElse(DialogIntent.CHAT);
        return switch (intent) {
            case GENERATE_PROJECT -> DelegateCodegenNode.NODE_NAME;
            case MODIFY_CODE -> DelegateIterateNode.NODE_NAME;
            case CHAT -> ChatReplyNode.NODE_NAME;
        };
    }

    /**
     * 获取编译后的图实例。
     *
     * @return CompiledGraph
     */
    public CompiledGraph<DialogState> getCompiledGraph() {
        return compiledGraph;
    }
}
