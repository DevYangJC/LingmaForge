package com.lingmaforge.backend.workbench.ai.node;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.RequirementAnalyzer;
import com.lingmaforge.backend.common.model.RequirementSpec;

/**
 * 节点一：需求分析。
 *
 * <p>把用户的自然语言需求翻译成结构化 {@link RequirementSpec}，供下游执行规划节点读取。
 * 使用 LangChain4j 的 AiServices 接口模式做结构化输出，零手动 JSON 解析。</p>
 *
 * <p>模型：DeepSeek-V3（OpenAI 兼容协议）；当前实现复用单一 ChatModel Bean。</p>
 */
@Component
public class RequirementAnalysisNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(RequirementAnalysisNode.class);

    /** 节点名称，对应 SSE 事件的 nodeName 字段。 */
    public static final String NODE_NAME = "requirement_analysis";

    private final RequirementAnalyzer analyzer;

    public RequirementAnalysisNode(AgentFactory agentFactory, GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.analyzer = agentFactory.createRequirementAnalyzer();
    }

    /**
     * 执行需求分析。
     *
     * @param state 流水线状态
     * @return 状态更新：写入 analysisResult
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在分析需求规格...");
        try {
            String userPrompt = state.prompt().orElseThrow();
            log.info("[{}] 需求分析开始: {}", state.taskId().orElse(""), userPrompt);
            RequirementSpec spec = analyzer.analyze(userPrompt);
            emitter.emitNode(NODE_NAME, "需求分析完成：应用 " + spec.appName(), "TEXT");
            return Map.of(CodeGenState.ANALYSIS_RESULT, spec);
        } catch (Exception e) {
            log.error("[{}] 需求分析失败", state.taskId().orElse(""), e);
            emitter.emitNode(NODE_NAME, "需求分析失败: " + e.getMessage(), "TEXT");
            throw e;
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }
}
