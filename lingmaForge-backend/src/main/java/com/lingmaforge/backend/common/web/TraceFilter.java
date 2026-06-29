package com.lingmaforge.backend.common.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 请求级别的 traceId 追踪过滤器。
 *
 * <p>在每次 HTTP 请求到达时生成唯一 traceId 并注入 {@link MDC}，使同一请求的所有日志自动携带该标识。
 * 处理完毕后清理 MDC，防止线程池复用时残留旧值导致日志串号。</p>
 *
 * <p>traceId 同时写入响应头 {@code X-Trace-Id}，前端可将该值附在报障信息中，方便日志回溯。</p>
 *
 * <p>过滤器优先级设为最高（{@link Ordered#HIGHEST_PRECEDENCE}），确保在所有业务逻辑之前执行。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    /** MDC 中的 key，对应 logback pattern 中的 %X{traceId}。 */
    public static final String MDC_TRACE_ID = "traceId";

    /** 响应头名称，前端可通过此头获取 traceId。 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = generateTraceId();
        try {
            MDC.put(MDC_TRACE_ID, traceId);
            response.setHeader(HEADER_TRACE_ID, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * 生成短 UUID 作为 traceId。
     *
     * <p>去掉 UUID 中的连字符并取前 12 位，兼顾唯一性与可读性。</p>
     *
     * @return 12 位字母数字 traceId
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
