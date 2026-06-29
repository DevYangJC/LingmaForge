package com.lingmaforge.backend.common.log;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingmaforge.backend.common.api.Result;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Controller 层请求日志切面。
 *通过 AOP 环绕通知自动记录每个 Controller 方法的关键信息：
 *排除 SpringDoc OpenAPI 和健康检查等噪音 Controller，不重复记录异常
 * （异常由 {@link com.lingmaforge.backend.common.exception.GlobalExceptionHandler} 统一处理）。</p>
 */
@Aspect
@Component
public class ControllerLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerLogAspect.class);

    /**
     * 参数日志最大长度，超出截断。
     */
    private static final int MAX_ARG_LENGTH = 500;

    /**
     * 返回值 data 日志最大长度。
     */
    private static final int MAX_DATA_LENGTH = 1000;

    private final ObjectMapper objectMapper;

    public ControllerLogAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 切点：所有 @RestController 方法，排除 SpringDoc 和健康检查。
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)"
            + " && !within(org.springdoc..*)"
            + " && !within(com.lingmaforge.backend.web.HealthController)")
    public void controllerMethods() {
    }

    /**
     * 环绕通知：记录入参、耗时、返回值。异常不记录，直接向上抛。
     */
    @Around("controllerMethods()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String argsStr = formatArgs(joinPoint.getArgs());

        log.info("[CTRL] {} {}({})", className, methodName, argsStr);
        Object result = joinPoint.proceed();
        long elapsed = System.currentTimeMillis() - startTime;

        if (result instanceof Result<?> r) {
            String dataStr = formatData(r.getData());
            log.info("[CTRL] {} {} | {}ms | success={} | data={}",
                    className, methodName, elapsed, r.isSuccess(), dataStr);
        } else {
            // SSE SseEmitter、SpringDoc 内部返回值等
            log.info("[CTRL] {} {} | {}ms | type={}",
                    className, methodName, elapsed,
                    result == null ? "null" : result.getClass().getSimpleName());
        }
        return result;
    }

    /**
     * 将方法参数数组转为可读字符串。
     *
     * 业务 DTO 用 Jackson 序列化，超长截断。
     */
    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        String joined = Arrays.stream(args)
                .map(this::formatSingleArg)
                .collect(Collectors.joining(", "));
        if (joined.length() > MAX_ARG_LENGTH) {
            return joined.substring(0, MAX_ARG_LENGTH) + "...";
        }
        return joined;
    }

    /**
     * 格式化返回值 data 字段。
     *
     * <ul>
     *   <li>null → "-"</li>
     *   <li>集合/数组 → 先输出元素个数，再跟序列化内容</li>
     *   <li>其它 → Jackson JSON，超长截断</li>
     * </ul>
     */
    private String formatData(Object data) {
        if (data == null) {
            return "-";
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            String prefix = "";
            if (data instanceof java.util.Collection<?> c) {
                prefix = "[" + c.size() + " items] ";
            } else if (data.getClass().isArray()) {
                prefix = "[" + java.lang.reflect.Array.getLength(data) + " items] ";
            }
            String body = prefix + json;
            if (body.length() > MAX_DATA_LENGTH) {
                return body.substring(0, MAX_DATA_LENGTH) + "...";
            }
            return body;
        } catch (Exception e) {
            return data.getClass().getSimpleName() + "(序列化失败)";
        }
    }

    /**
     * 格式化单个参数。
     *
     * <ul>
     *   <li>null → "null"</li>
     *   <li>Servlet 容器对象 → 类型名（如 "RequestFacade"）</li>
     *   <li>其它 → Jackson JSON 序列化</li>
     * </ul>
     */
    private String formatSingleArg(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof HttpSession) {
            return arg.getClass().getSimpleName();
        }
        try {
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            return arg.getClass().getSimpleName() + "(序列化失败)";
        }
    }
}
