package com.lingmaforge.backend.workbench.ai.dialog;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.service.IntentAnalyzer;

/**
 * 意图识别节点。
 *
 * <p>对话图的第一节点，用便宜模型（deepseek-flash）做结构化输出，返回 {@link IntentResult}。
 * 识别失败时回退为 {@link DialogIntent#CHAT}（最安全，不触发任何代码操作），保证图不中断。</p>
 */
@Component
public class IntentDetectionNode {

    private static final Logger log = LoggerFactory.getLogger(IntentDetectionNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "intent_detection";

    private final IntentAnalyzer analyzer;

    public IntentDetectionNode(AgentFactory agentFactory) {
        this.analyzer = agentFactory.createIntentAnalyzer();
    }

    /**
     * 执行意图识别。
     *
     * @param state 对话状态
     * @return 状态更新：写入 INTENT 与 INTENT_CONFIDENCE
     */
    public Map<String, Object> execute(DialogState state) {
        String userMessage = state.userMessage().orElse("");
        log.info("[意图识别] 开始分析: {}", userMessage);
        try {
            IntentResult result = analyzer.analyze(userMessage);
            DialogIntent intent = DialogIntent.fromType(result.intent());
            // confidence 可能为 null（模型未返回该字段），回退为 0.0
            double confidence = result.confidence() != null ? result.confidence() : 0.0;
            if (intent == null) {
                log.warn("[意图识别] 未识别的意图类型 [{}]，回退为 CHAT", result.intent());
                intent = DialogIntent.CHAT;
                confidence = 0.0;
            }
            log.info("[意图识别] 结果: intent={}, confidence={}", intent.getType(), confidence);
            return Map.of(
                    DialogState.INTENT, intent,
                    DialogState.INTENT_CONFIDENCE, confidence);
        } catch (Exception e) {
            // 兜底：模型不可用或解析异常时回退为闲聊，保证图继续流转
            log.warn("[意图识别] 识别失败，回退为 CHAT: {}", e.getMessage());
            return Map.of(
                    DialogState.INTENT, DialogIntent.CHAT,
                    DialogState.INTENT_CONFIDENCE, 0.0);
        }
    }
}
