package com.lingmaforge.backend.ai.node;

import com.lingmaforge.backend.ai.observer.GenerationContext;
import com.lingmaforge.backend.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.ai.pipeline.CodeGenState;

/**
 * 代码生成节点的抽象基类。
 *
 * <p>提供线程上下文注入的模板方法：节点执行前从注册中心按 taskId 取出发射器，
 * 注入 {@link GenerationContext} 的 ThreadLocal，使 @Tool 方法能获取项目 ID 并推送 SSE 事件；
 * 执行完成后清理，防止内存泄漏。</p>
 *
 * <p>LangGraph4j 的图节点可能在 fork-join 线程上执行，因此必须在节点入口处重新注入上下文，
 * 而不能依赖调用线程的 ThreadLocal 传递。</p>
 */
public abstract class AbstractCodeGenNode {

    private final GenerationStreamRegistry streamRegistry;

    protected AbstractCodeGenNode(GenerationStreamRegistry streamRegistry) {
        this.streamRegistry = streamRegistry;
    }

    /**
     * 注入当前任务的生成上下文。
     *
     * @param state 流水线状态
     * @return SSE 事件发射器
     */
    protected GenerationStreamEmitter setupContext(CodeGenState state) {
        Long projectId = parseProjectId(state);
        String taskId = state.taskId().orElseThrow();
        GenerationStreamEmitter emitter = streamRegistry.get(taskId);
        GenerationContext.set(projectId, taskId, emitter);
        return emitter;
    }

    /**
     * 清理当前线程的生成上下文。
     */
    protected void clearContext() {
        GenerationContext.clear();
    }

    private Long parseProjectId(CodeGenState state) {
        return Long.parseLong(state.projectId().orElseThrow(
                () -> new IllegalStateException("状态中缺少 projectId")));
    }

    /**
     * 从状态中解析项目 ID。
     *
     * @param state 流水线状态
     * @return 项目 ID
     */
    protected Long projectId(CodeGenState state) {
        return parseProjectId(state);
    }
}
