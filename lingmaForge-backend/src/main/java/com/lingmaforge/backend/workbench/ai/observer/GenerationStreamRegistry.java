package com.lingmaforge.backend.workbench.ai.observer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 生成流发射器的按任务注册中心。
 *
 * <p>LangGraph4j 的图节点可能在 fork-join 线程上执行，无法依赖调用线程的 ThreadLocal 传递
 * SSE 发射器。故由 {@code GenerationService} 在任务启动时注册发射器，节点通过 taskId 查询。</p>
 *
 * <p>同时维护"已请求停止"的任务 ID 集合，供节点内的流式循环定期检查，
 * 实现 LLM 调用期间的即时停止响应。</p>
 */
@Component
public class GenerationStreamRegistry {

    private final ConcurrentHashMap<String, GenerationStreamEmitter> emitters = new ConcurrentHashMap<>();

    /** 已标记停止的任务 ID 集合。 */
    private final Set<String> stoppedTasks = ConcurrentHashMap.newKeySet();

    /**
     * 注册任务发射器。
     *
     * @param taskId  任务 ID
     * @param emitter SSE 事件发射器
     */
    public void register(String taskId, GenerationStreamEmitter emitter) {
        emitters.put(taskId, emitter);
    }

    /**
     * 获取任务发射器。
     *
     * @param taskId 任务 ID
     * @return 发射器，不存在返回 null
     */
    public GenerationStreamEmitter get(String taskId) {
        return emitters.get(taskId);
    }

    /**
     * 注销任务发射器。
     *
     * @param taskId 任务 ID
     */
    public void unregister(String taskId) {
        emitters.remove(taskId);
        stoppedTasks.remove(taskId);
    }

    /**
     * 请求停止指定任务。
     *
     * @param taskId 任务 ID
     */
    public void requestStop(String taskId) {
        stoppedTasks.add(taskId);
    }

    /**
     * 检查指定任务是否已被请求停止。
     *
     * @param taskId 任务 ID
     * @return 是否已请求停止
     */
    public boolean isStopRequested(String taskId) {
        return stoppedTasks.contains(taskId);
    }

    /**
     * 获取停止任务集合（只读视图），供调试使用。
     *
     * @return 停止任务 ID 集合
     */
    public Set<String> getStoppedTasks() {
        return stoppedTasks;
    }
}
