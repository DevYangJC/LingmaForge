package com.lingmaforge.backend.common.model;

/**
 * 发送给客户端的图节点进度载荷，通过 SSE 推送给前端展示 Agent 工作流进度。
 *
 * @param threadId 线程标识，关联当前 SSE 流
 * @param nodeName 节点名称（如 plan / generate / build / verify）
 * @param text     节点输出的文本内容
 * @param textType 文本类型（如 markdown / log / json）
 */
public record GraphNodeResponse(
        String threadId,
        String nodeName,
        String text,
        String textType) {
}
