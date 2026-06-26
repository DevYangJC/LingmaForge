package com.lingmaforge.backend.ai.observer;

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
}
