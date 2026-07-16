package com.lingmaforge.backend.workbench.ai.stream;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;

import reactor.core.publisher.FluxSink;

/**
 * 把 {@link GenerationStreamEmitter} 的高层语义事件统一封装为 {@link StreamEvent}，
 * 并经 {@link FluxSink} 推送为 {@code Flux<ServerSentEvent<String>>}。
 *
 * <p>这是"流水线节点/工具事件"到"统一流式封装 + WebFlux SSE"的桥接核心。
 * 所有 SSE event 名收敛为五类：token / thinking / tool_call / done / error；
 * 节点生命周期、文件落盘等丰富语义折叠进 {@code token} 事件的 {@code kind} 字段。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li>线程安全：由 {@code GenerationService} 在单一生成线程驱动，{@code FluxSink} 本身线程安全。</li>
 *   <li>幂等完成：{@link #complete(String, Integer, Integer)} 只推一次 {@code done} 事件；
 *       重复调用被 {@link #doneEmitted} 标记吞掉，避免前端重复收到完成信号。</li>
 *   <li>发送失败容忍：连接中途断开时 {@link FluxSink#next} 不抛异常（Reactor 丢弃），
 *       仅记录 debug 日志，不影响生成线程继续落盘。</li>
 * </ul>
 */
public class FluxStreamEmitter implements GenerationStreamEmitter {

    private static final Logger log = LoggerFactory.getLogger(FluxStreamEmitter.class);

    private final String taskId;
    private final FluxSink<ServerSentEvent<String>> sink;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean doneEmitted = new AtomicBoolean(false);

    public FluxStreamEmitter(String taskId, FluxSink<ServerSentEvent<String>> sink, ObjectMapper objectMapper) {
        this.taskId = taskId;
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    public FluxSink<ServerSentEvent<String>> sink() {
        return sink;
    }

    private void emit(StreamEvent event) {
        try {
            ServerSentEvent<String> sse = ServerSentEvent.<String>builder()
                    .event(event.getType())
                    .data(event.toJson(objectMapper))
                    .build();
            sink.next(sse);
        } catch (Exception e) {
            log.debug("FluxStreamEmitter 推送失败（连接可能已关闭）: taskId={}, type={}", taskId, event.getType());
        }
    }

    @Override
    public void emitNode(String nodeName, String text, String textType) {
        emit(StreamEvent.nodeText(nodeName, text, textType));
    }

    @Override
    public void emitFile(String path, String content, String status) {
        emit(StreamEvent.file(path, content, status));
    }

    @Override
    public void emitLog(String text) {
        emit(StreamEvent.log(text));
    }

    @Override
    public void complete(String url, Integer port, Integer buildTime) {
        if (doneEmitted.compareAndSet(false, true)) {
            emit(StreamEvent.done(url, port, buildTime));
        }
    }

@Override
    public void error(String message) {
        emit(StreamEvent.error(null, message));
    }

    @Override
    public void emitNodeStart(String nodeName, String title) {
        emit(StreamEvent.nodeStart(nodeName, title));
    }

    @Override
    public void emitNodeEnd(String nodeName) {
        emit(StreamEvent.nodeEnd(nodeName));
    }

    @Override
    public void emitThinking(String nodeName, String token) {
        emit(StreamEvent.thinking(nodeName, token));
    }

    @Override
    public void emitFileToken(String path, String token) {
        emit(StreamEvent.fileToken(path, token));
    }

    @Override
    public void emitFileComplete(String path) {
        emit(StreamEvent.fileComplete(path));
    }

    @Override
    public void emitToolCall(String id, String name, String arguments, String result) {
        // 参数过长截断，避免单条 SSE 过大造成前端卡顿
        String trimmedArgs = arguments;
        if (trimmedArgs != null && trimmedArgs.length() > 4000) {
            trimmedArgs = trimmedArgs.substring(0, 4000) + "...(截断)";
        }
        String trimmedResult = result;
        if (trimmedResult != null && trimmedResult.length() > 4000) {
            trimmedResult = trimmedResult.substring(0, 4000) + "...(截断)";
        }
        emit(StreamEvent.toolCall(id, name, trimmedArgs, trimmedResult));
    }

    /**
     * 兼容旧 {@code MediaType.APPLICATION_JSON} 推送语义的提示标记（目前未使用）。
     *
     * <p>占位以便编译期类型可被 {@code GenerationService} 在需要时转换为 JSON data type。</p>
     */
    @SuppressWarnings("unused")
    private MediaType unusedMedia() {
        return MediaType.APPLICATION_JSON;
    }
}