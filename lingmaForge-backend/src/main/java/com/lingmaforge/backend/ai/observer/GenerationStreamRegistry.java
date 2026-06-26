package com.lingmaforge.backend.ai.observer;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 生成流发射器的按任务注册中心。
 *
 * <p>LangGraph4j 的图节点可能在 fork-join 线程上执行，无法依赖调用线程的 ThreadLocal 传递
 * SSE 发射器。故由 {@code GenerationService} 在任务启动时注册发射器，节点通过 taskId 查询。</p>
 */
@Component
public class GenerationStreamRegistry {

    private final ConcurrentHashMap<String, GenerationStreamEmitter> emitters = new ConcurrentHashMap<>();

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
    }
}
