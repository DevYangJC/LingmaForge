package com.lingmaforge.backend.workbench.ai.dialog;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 代码生成桥接桩节点。
 *
 * <p>Phase 1 只返回占位状态，Phase 4 将在此把对话状态转成 {@code CodeGenState}，
 * 调用现有 {@code CodeGenPipeline.getCompiledGraph().stream(inputs)} 完成项目生成。</p>
 *
 * <p><b>Phase 4 衔接注记</b>：桥接 CodeGenPipeline 时需注入 {@code GenerationContext}
 * ThreadLocal（供 {@code @Tool} 方法获取 projectId 与 emitter），届时应继承
 * {@code AbstractCodeGenNode} 或在 execute 入口/出口手动管理上下文。</p>
 */
@Component
public class DelegateCodegenNode {

    private static final Logger log = LoggerFactory.getLogger(DelegateCodegenNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "delegate_codegen";

    /**
     * 执行桥接（桩实现）。
     *
     * @param state 对话状态
     * @return 占位状态更新
     */
    public Map<String, Object> execute(DialogState state) {
        log.info("[delegate_codegen] 桩节点执行 — 桥接逻辑将在 Phase 4 实现");
        return Map.of(DialogState.DELEGATE_RESULT,
                "[桩] 已识别为生成项目意图，桥接逻辑将在 Phase 4 实现");
    }
}
