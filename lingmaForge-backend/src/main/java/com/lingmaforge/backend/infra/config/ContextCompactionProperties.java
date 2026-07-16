package com.lingmaforge.backend.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上下文自动压缩配置——对齐 zero-code {@code ContextCompactionProperties}。
 *
 * <p>对应 {@code application-dev.yml} 中的 {@code lingma.compaction} 配置段。</p>
 *
 * @param tokenThreshold       触发自动压缩的 token 估算阈值（建议 ≤ 模型最大上下文的 80%）
 * @param maxSummaryInputChars 送入摘要 LLM 的最大字符数，避免摘要请求本身超长
 */
@ConfigurationProperties(prefix = "lingma.compaction")
public record ContextCompactionProperties(
        int tokenThreshold,
        int maxSummaryInputChars) {

    /** 默认 DeepSeek V4-Pro 1M context × 80% = 800k tokens。 */
    public static final int DEFAULT_TOKEN_THRESHOLD = 800_000;

    /** 默认 300k 字符，约 100k tokens，轻量摘要模型可轻松处理。 */
    public static final int DEFAULT_MAX_SUMMARY_INPUT_CHARS = 300_000;
}