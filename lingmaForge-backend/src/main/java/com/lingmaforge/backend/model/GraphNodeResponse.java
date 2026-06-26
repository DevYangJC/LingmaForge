package com.lingmaforge.backend.model;

/**
 * 发送给客户端的图节点进度载荷。
 */
public record GraphNodeResponse(
        String threadId,
        String nodeName,
        String text,
        String textType) {
}
