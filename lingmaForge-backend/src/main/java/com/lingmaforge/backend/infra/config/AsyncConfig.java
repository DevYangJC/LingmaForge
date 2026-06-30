package com.lingmaforge.backend.infra.config;

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

    /** 文件生成子任务执行器 Bean 名称。 */
    public static final String FILE_GEN_EXECUTOR = "fileGenExecutor";

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

    /**
     * 文件生成子任务线程池。
     *
     * <p>与 {@link #generationExecutor()} 隔离，防止全局线程池被 pipeline 线程占满后
     * 子任务无法获取线程导致的死锁。使用缓存的线程池支持突发并行文件生成。</p>
     *
     * @return 执行器
     */
    @Bean(name = FILE_GEN_EXECUTOR)
    public Executor fileGenExecutor() {
        return java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "file-gen-");
            thread.setDaemon(true);
            return thread;
        });
    }
}
