package com.exam.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置 (v4.0)：主观题 AI 判分专用有界线程池。
 * <p>目的：把耗时的远程 AI 调用彻底移出请求线程与数据库事务。线程池规模刻意小于
 * HikariCP 连接池上限（dev 5 / prod 20），并采用有界队列 + Abort 拒绝策略——
 * 被拒绝的任务对应的主观题仍停留在 pending_ai，由补偿扫描器重试，绝不丢单。</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String AI_GRADING_EXECUTOR = "aiGradingExecutor";

    @Bean(name = AI_GRADING_EXECUTOR)
    public Executor aiGradingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-grade-");
        // 队列满时直接拒绝，交由补偿扫描重试；不做 CallerRuns 以免把 AI 调用压回提交线程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // 兜底记录异步方法内未捕获异常（判分逻辑内部已 try/catch，这里防线程池静默吞异常）
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
