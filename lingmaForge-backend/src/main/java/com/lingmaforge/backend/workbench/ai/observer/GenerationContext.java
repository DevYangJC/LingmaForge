package com.lingmaforge.backend.workbench.ai.observer;

/**
 * 生成任务的线程上下文。
 *
 * <p>工具方法（@Tool）在 Agent 循环中被调用时，需要知道当前所属的项目 ID、任务 ID
 * 以及向哪个 SSE 连接推送事件。由于 LangChain4j 的工具方法签名固定，无法直接传入这些值，
 * 故通过 {@link ThreadLocal} 在节点执行前注入、执行后清理。</p>
 *
 * <p><b>注意</b>：LangChain4j 的 {@code AiServices} 默认在调用线程同步执行工具，
 * 因此 ThreadLocal 能在整条 Agent 循环中正确传递。</p>
 */
public final class GenerationContext {

    private static final ThreadLocal<GenerationContext> HOLDER = new ThreadLocal<>();

    private final Long projectId;
    private final String taskId;
    private final GenerationStreamEmitter emitter;

    private GenerationContext(Long projectId, String taskId, GenerationStreamEmitter emitter) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.emitter = emitter;
    }

    /**
     * 设置当前线程的生成上下文。
     *
     * @param projectId 项目 ID
     * @param taskId    任务 ID
     * @param emitter   SSE 事件发射器
     */
    public static void set(Long projectId, String taskId, GenerationStreamEmitter emitter) {
        HOLDER.set(new GenerationContext(projectId, taskId, emitter));
    }

    /**
     * 获取当前线程的生成上下文。
     *
     * @return 生成上下文，未设置时抛异常
     */
    public static GenerationContext get() {
        GenerationContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("当前线程未设置 GenerationContext，无法执行工具调用");
        }
        return context;
    }

    /**
     * 清理当前线程的生成上下文，防止内存泄漏。
     */
    public static void clear() {
        HOLDER.remove();
    }

    public Long projectId() {
        return projectId;
    }

    public String taskId() {
        return taskId;
    }

    public GenerationStreamEmitter emitter() {
        return emitter;
    }
}
