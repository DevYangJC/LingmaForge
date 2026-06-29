package com.lingmaforge.backend.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 沙箱与构建相关配置项。
 *
 * <p>构建验证默认关闭（build-enabled=false），流水线可在无 Node.js 环境下端到端跑通；
 * 开启后通过 {@link java.lang.ProcessBuilder} 在项目工作区执行真实的 npm install / npm run build。</p>
 */
@ConfigurationProperties(prefix = "lingma.sandbox")
public record LingmaSandboxProperties(
        boolean buildEnabled,
        String nodeBinary,
        String npmBinary,
        int buildTimeoutSeconds,
        String basePreviewHost,
        int previewPort) {
}
