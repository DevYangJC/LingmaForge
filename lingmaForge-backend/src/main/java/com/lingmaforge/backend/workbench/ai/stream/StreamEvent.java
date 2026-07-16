package com.lingmaforge.backend.workbench.ai.stream;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 统一流式消息封装。
 *
 * <p>移植自 zero-code-monolith 的 {@code StreamMessage} 体系，并按本次要求收敛为五类事件：
 * <ul>
 *   <li><b>token</b>     —— 普通 Token / 文本增量（含节点进度文案、文件代码 Token、整文件、日志、节点生命周期）</li>
 *   <li><b>thinking</b>  —— 深度推理过程 Token（reasoning_content 回放通道）</li>
 *   <li><b>tool_call</b> —— 工具调用（工具名 + 参数 + 结果），对应 zero-code 的 tool_request / tool_executed</li>
 *   <li><b>done</b>      —— 整条流水线完成（含预览地址、端口、构建耗时）</li>
 *   <li><b>error</b>     —— 任意阶段失败</li>
 * </ul>
 *
 * <p>SSE 传输约定：SSE 事件名（{@code event:}）= {@link #type}，{@code data:} = {@link #data} 的 JSON。
 * 由于现有流水线语义比 five-type 更丰富（节点开始/结束、文件落盘、日志等），
 * 故在 {@code token} 事件内用 {@code kind} 二级标识区分，前端据此分发渲染，
 * 既保证统一封装又兼容既有前后端信息。</p>
 *
 * <p>本类为不可变值对象，所有工厂方法返回新实例，序列化时 {@link JsonInclude.Include#NON_NULL}
 * 忽略空字段以保持报文精简。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamEvent {

    /** 事件类型。 */
    private final String type;

    /** 载荷（自由结构，由 kind 区分子语义）。 */
    private final Map<String, Object> data;

    public StreamEvent(String type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }

    // ===================== 工厂方法 =====================

    public static StreamEvent token(String kind, Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (kind != null) {
            data.put("kind", kind);
        }
        if (payload != null) {
            data.putAll(payload);
        }
        return new StreamEvent("token", data);
    }

    public static StreamEvent thinking(String nodeName, String token) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (nodeName != null) {
            data.put("nodeName", nodeName);
        }
        data.put("token", token == null ? "" : token);
        return new StreamEvent("thinking", data);
    }

    public static StreamEvent toolCall(String id, String name, String arguments, String result) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (id != null) {
            data.put("id", id);
        }
        data.put("name", name);
        if (arguments != null) {
            data.put("arguments", arguments);
        }
        // result 为 null 表示"调用中"阶段；非空表示已完成
        data.put("result", result);
        return new StreamEvent("tool_call", data);
    }

    public static StreamEvent done(String url, Integer port, Integer buildTime) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url == null ? "" : url);
        data.put("port", port == null ? 0 : port);
        data.put("buildTime", buildTime == null ? 0 : buildTime);
        return new StreamEvent("done", data);
    }

    public static StreamEvent error(String nodeName, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (nodeName != null) {
            data.put("nodeName", nodeName);
        }
        data.put("message", message == null ? "" : message);
        return new StreamEvent("error", data);
    }

    // ===================== 具体语义快捷工厂 =====================

    public static StreamEvent nodeStart(String nodeName, String title) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nodeName", nodeName);
        p.put("text", title == null ? "" : title);
        return token("node_start", p);
    }

    public static StreamEvent nodeEnd(String nodeName) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nodeName", nodeName);
        return token("node_end", p);
    }

    public static StreamEvent nodeText(String nodeName, String text, String textType) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nodeName", nodeName);
        p.put("text", text == null ? "" : text);
        if (textType != null) {
            p.put("textType", textType);
        }
        return token("node_text", p);
    }

    public static StreamEvent fileToken(String path, String token) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("path", path);
        p.put("token", token == null ? "" : token);
        return token("file_token", p);
    }

    public static StreamEvent fileComplete(String path) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("path", path);
        return token("file_complete", p);
    }

    public static StreamEvent file(String path, String content, String status) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("path", path);
        p.put("content", content == null ? "" : content);
        p.put("status", status == null ? "new" : status);
        return token("file", p);
    }

    public static StreamEvent log(String text) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("text", text == null ? "" : text);
        return token("log", p);
    }

    /**
     * 序列化为 SSE data 字段使用的 JSON 字符串。
     *
     * @param mapper Jackson ObjectMapper（调用方注入以复用）
     * @return JSON 字符串
     */
    public String toJson(ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(this.data);
        } catch (Exception e) {
            return "{}";
        }
    }
}