package com.lingmaforge.backend.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务执行器配置。
 *
 * <p>为生成流水线的 SSE 流式执行提供独立线程池，避免阻塞容器请求线程。</p>
 */
@Configuration
public class AsyncConfig {

    /** 生成流水线执行器 Bean 名称。 */
    public static final String GENERATION_EXECUTOR = "generationExecutor";

    /**
     * 生成流水线线程池。
     *
     * @return 执行器
     */
    @Bean(name = GENERATION_EXECUTOR)
    public Executor generationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(64);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("generation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
