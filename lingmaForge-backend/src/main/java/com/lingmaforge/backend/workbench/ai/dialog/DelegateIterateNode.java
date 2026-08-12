package com.lingmaforge.backend.workbench.ai.dialog;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 代码修改桥接桩节点。
 *
 * <p>Phase 1 只返回占位状态，Phase 3 将在此调用已有 {@code IterationAgent} + {@code IterationTools}
 * 完成增量补丁修改。</p>
 *
 * <p><b>Phase 3 衔接注记</b>：接入 SSE 时需注入 {@code GenerationContext} ThreadLocal
 * （供迭代工具获取 projectId、emitter 与文件路径），或改用流式 emitter 将补丁逐步推送给前端。</p>
 */
@Component
public class DelegateIterateNode {

    private static final Logger log = LoggerFactory.getLogger(DelegateIterateNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "delegate_iterate";

    /**
     * 执行桥接（桩实现）。
     *
     * @param state 对话状态
     * @return 占位状态更新
     */
    public Map<String, Object> execute(DialogState state) {
        log.info("[delegate_iterate] 桩节点执行 — 桥接逻辑将在 Phase 3 实现");
        return Map.of(DialogState.DELEGATE_RESULT,
                "[桩] 已识别为代码修改意图，桥接逻辑将在 Phase 3 实现");
    }
}
