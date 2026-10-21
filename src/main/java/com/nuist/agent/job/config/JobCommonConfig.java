package com.nuist.agent.job.config;

import com.nuist.agent.job.ai.LlmResultCache;
import com.nuist.agent.job.ai.PromptTemplateRenderer;
import com.nuist.agent.job.ai.SlidingWindowRateLimiter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智能求职辅助系统公共配置（成员 A：公共组件）。
 * 提供：配置属性绑定、AI 网关所需的超时执行线程池、限流器、结果缓存、模板渲染器与 JSON 工具。
 */
@Configuration
@EnableConfigurationProperties(JobProperties.class)
public class JobCommonConfig {

    private static final Logger log = LoggerFactory.getLogger(JobCommonConfig.class);

    /**
     * 大模型调用线程池：用于给同步 ChatClient 调用加“超时保护”，
     * 避免 SDK 内部重试导致请求线程被长时间占用。
     */
    @Bean(name = "jobAiExecutor", destroyMethod = "")
    public ExecutorService jobAiExecutor(JobProperties properties) {
        int threads = Math.max(4, properties.getAi().getRateLimitPerMinute());
        AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "job-ai-" + seq.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200), factory, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 单用户滑动窗口限流器（AI 调用频率控制） */
    @Bean
    public SlidingWindowRateLimiter jobRateLimiter() {
        return new SlidingWindowRateLimiter();
    }

    /** AI 结果缓存（相同输入短时间内复用，降低费用与延迟） */
    @Bean
    public LlmResultCache jobLlmResultCache(JobProperties properties) {
        return new LlmResultCache(properties.getAi().getCacheMaxEntries(),
                properties.getAi().getCacheTtlSeconds() * 1000L);
    }

    /** 提示词模板渲染器（{变量} 占位替换） */
    @Bean
    public PromptTemplateRenderer promptTemplateRenderer() {
        return new PromptTemplateRenderer();
    }

    @PreDestroy
    public void shutdown() {
        log.info("智能求职辅助系统配置销毁");
    }
}
