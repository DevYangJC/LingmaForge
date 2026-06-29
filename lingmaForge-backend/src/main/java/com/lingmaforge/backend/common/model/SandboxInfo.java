package com.lingmaforge.backend.common.model;

/**
 * 沙箱运行信息。
 *
 * @param url  预览 URL
 * @param port 预览端口
 * @param status 沙箱状态：running / stopped
 */
public record SandboxInfo(String url, int port, String status) {
}
