package com.lingmaforge.backend.workbench.ai.observer;

/**
 * 生成流水线事件发射器契约。
 *
 * <p>由 {@code GenerationService} 针对每次生成任务提供实现，工具与节点通过它向前端推送 SSE 事件。</p>
 */
public interface GenerationStreamEmitter {

    /**
     * 推送节点进度事件。
     *
     * @param nodeName 节点名称
     * @param text     增量内容
     * @param textType 文本类型：TEXT / JSON / MARK_DOWN / HTML
     */
    void emitNode(String nodeName, String text, String textType);

    /**
     * 推送文件生成事件（前端文件树新增节点、编辑器注入代码）。
     *
     * @param path    文件路径
     * @param content 文件内容
     * @param status  文件状态：new / modified
     */
    void emitFile(String path, String content, String status);

    /**
     * 推送运行日志事件。
     *
     * @param text 日志文本
     */
    void emitLog(String text);

    /**
     * 推送完成事件。
     *
     * @param url       预览 URL
     * @param port      预览端口
     * @param buildTime 构建耗时（秒）
     */
    void complete(String url, Integer port, Integer buildTime);

    /**
     * 推送错误事件。
     *
     * @param message 错误信息
     */
    void error(String message);

    /**
     * 推送节点开始事件。
     *
     * @param nodeName 节点名称
     * @param title    展示标题
     */
    void emitNodeStart(String nodeName, String title);

    /**
     * 推送节点结束事件。
     *
     * @param nodeName 节点名称
     */
    void emitNodeEnd(String nodeName);

    /**
     * 推送思考过程中的 Token。
     *
     * @param nodeName 节点名称
     * @param token    思考 Token
     */
    void emitThinking(String nodeName, String token);

    /**
     * 推送工具调用事件（统一封封装中的 tool_call 事件类型）。
     *
     * <p>当 Agent 通过 @Tool 方法执行文件写入、补丁、上下文读取、代码搜索等动作时，
     * 在工具执行前后推送该事件，使前端实时可见"模型正在调用什么工具、用了什么参数、得到什么结果"，
     * 从而复刻 zero-code 中 TokenStream 的 beforeToolExecution / onToolExecuted 可视化能力。</p>
     *
     * @param id        工具调用 ID（可传 null）：用于关联请求与结果
     * @param name      工具名称（如 writeFile / patchFile / readFileContext）
     * @param arguments 工具参数（JSON 字符串，过长可截断）
     * @param result    工具执行结果文本；为 null 表示"调用中"阶段，非空表示"已完成"阶段
     */
    void emitToolCall(String id, String name, String arguments, String result);

    /**
     * 流式推送单个文件的代码 Token。
     *
     * @param path  文件路径
     * @param token 代码 Token
     */
    void emitFileToken(String path, String token);

    /**
     * 流式推送某个文件生成完成的信号。
     *
     * @param path 文件路径
     */
    void emitFileComplete(String path);
}
