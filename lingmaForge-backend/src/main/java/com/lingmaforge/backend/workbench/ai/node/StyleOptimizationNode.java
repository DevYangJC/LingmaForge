package com.lingmaforge.backend.workbench.ai.node;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.workbench.ai.service.StyleOptimizationAgent;

/**
 * 节点四：样式优化。
 *
 * <p>对已生成的样式文件做增量微调，通过 patchFile 工具应用补丁，保留用户手动修改。
 * 模型：DeepSeek-V3；当前实现复用单一 ChatModel Bean。</p>
 */
@Component
public class StyleOptimizationNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(StyleOptimizationNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "style_optimization";

    private final StyleOptimizationAgent agent;

    public StyleOptimizationNode(AgentFactory agentFactory, GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.agent = agentFactory.createStyleOptimizationAgent();
    }

    /**
     * 执行样式优化。
     *
     * @param state 流水线状态
     * @return 状态更新（空，样式修改通过 patchFile 工具直接生效）
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在进行样式微调与视觉优化...");
        try {
            List<String> stylePaths = collectStylePaths(state);
            if (stylePaths.isEmpty()) {
                emitter.emitNode(NODE_NAME, "无样式文件可优化，跳过", "TEXT");
                return Map.of();
            }
            String prompt = "请优化以下样式文件的视觉一致性与间距比例，通过 patchFile 工具应用增量补丁：\n"
                    + String.join("\n", stylePaths);
            log.info("[{}] 样式优化开始: {} 个文件", state.taskId().orElse(""), stylePaths.size());
            agent.optimize(prompt);
            emitter.emitNode(NODE_NAME, "样式优化完成", "TEXT");
            return Map.of();
        } catch (Exception e) {
            log.error("[{}] 样式优化失败", state.taskId().orElse(""), e);
            emitter.emitNode(NODE_NAME, "样式优化失败: " + e.getMessage(), "TEXT");
            // 样式优化失败不阻断流水线，继续构建验证
            return Map.of();
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }

    private List<String> collectStylePaths(CodeGenState state) {
        List<String> paths = state.generatedFiles()
                .map(files -> files.stream().map(f -> f.path()).toList())
                .orElse(List.of());
        return paths.stream().filter(p -> p.endsWith(".css")).toList();
    }
}
