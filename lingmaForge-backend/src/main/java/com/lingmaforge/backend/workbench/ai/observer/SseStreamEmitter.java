package com.lingmaforge.backend.workbench.ai.observer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.common.model.FileModification;

/**
 * 基于 {@link SseEmitter} 的 {@link GenerationStreamEmitter} 实现。
 *
 * <p>每个生成任务 / 对话会话对应一个实例，由 Service 层创建并注册到
 * {@link GenerationStreamRegistry}。所有事件经 Jackson 序列化为 JSON 后通过 SSE 推送。</p>
 *
 * <p>原为 {@code GenerationService} 的 private static 内部类，Phase 2 提取为顶层类，
 * 供 {@code GenerationService} 与 {@code ChatService} 共用，避免代码重复。</p>
 */
public class SseStreamEmitter implements GenerationStreamEmitter {

    private static final Logger log = LoggerFactory.getLogger(SseStreamEmitter.class);

    private final String taskId;
    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;

    public SseStreamEmitter(String taskId, SseEmitter emitter, ObjectMapper objectMapper) {
        this.taskId = taskId;
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void emitNode(String nodeName, String text, String textType) {
        send("message", Map.of(
                "threadId", taskId,
                "nodeName", nodeName,
                "text", text,
                "textType", textType,
                "error", false));
    }

    @Override
    public void emitFile(String path, String content, String status) {
        send("file", Map.of(
                "threadId", taskId,
                "path", path,
                "content", content,
                "status", status));
    }

    @Override
    public void emitLog(String text) {
        send("log", Map.of("threadId", taskId, "text", text));
    }

    @Override
    public void complete(String url, Integer port, Integer buildTime) {
        send("complete", Map.of(
                "threadId", taskId,
                "url", url == null ? "" : url,
                "port", port == null ? 0 : port,
                "buildTime", buildTime == null ? 0 : buildTime));
    }

    @Override
    public void error(String message) {
        send("error", Map.of(
                "threadId", taskId,
                "nodeName", "error",
                "text", message,
                "error", true));
    }

    @Override
    public void emitModification(String nodeName, String text, String textType,
            List<FileModification> modifications) {
        Map<String, Object> data = new HashMap<>();
        data.put("threadId", taskId);
        data.put("nodeName", nodeName);
        data.put("text", text);
        data.put("textType", textType);
        data.put("error", false);
        data.put("modifications", modifications);
        send("message", data);
    }

    @Override
    public void emitNodeStart(String nodeName, String title) {
        send("node_start", Map.of(
                "threadId", taskId,
                "nodeName", nodeName,
                "title", title));
    }

    @Override
    public void emitNodeEnd(String nodeName) {
        send("node_end", Map.of(
                "threadId", taskId,
                "nodeName", nodeName));
    }

    @Override
    public void emitThinking(String nodeName, String token) {
        send("thinking", Map.of(
                "threadId", taskId,
                "nodeName", nodeName,
                "token", token));
    }

    @Override
    public void emitFileToken(String path, String token) {
        send("file_token", Map.of(
                "threadId", taskId,
                "path", path,
                "token", token));
    }

    @Override
    public void emitFileComplete(String path) {
        send("file_complete", Map.of(
                "threadId", taskId,
                "path", path));
    }

    @Override
    public void emitChatToken(String token) {
        send("message", Map.of(
                "threadId", taskId,
                "nodeName", "chat_reply",
                "text", token,
                "textType", "TEXT",
                "error", false));
    }

    @Override
    public void emitChatComplete(String fullResponse) {
        Map<String, Object> data = new HashMap<>();
        data.put("threadId", taskId);
        data.put("nodeName", "chat_reply");
        data.put("text", fullResponse);
        data.put("textType", "TEXT");
        data.put("error", false);
        data.put("complete", true);
        send("message", data);
    }

    private void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 发送失败（连接可能已关闭）: taskId={}, event={}", taskId, event);
        }
    }

    /**
     * 安全关闭 SSE 连接（吞掉异常）。
     */
    public void safeComplete() {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE complete 异常: taskId={}", taskId);
        }
    }
}
