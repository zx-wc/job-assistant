package com.nuist.agent.job.config;

import com.nuist.agent.job.auth.JobAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置（成员 A：公共组件 / 权限入口）。
 * 只对 /api/job/** 生效，不影响既有 /ai/agent/**、/user/** 接口与静态资源。
 */
@Configuration
public class JobWebConfig implements WebMvcConfigurer {

    private final JobAuthInterceptor jobAuthInterceptor;

    public JobWebConfig(JobAuthInterceptor jobAuthInterceptor) {
        this.jobAuthInterceptor = jobAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jobAuthInterceptor)
                .addPathPatterns("/api/job/**")
                .excludePathPatterns(
                        "/api/job/auth/login",
                        "/api/job/auth/register",
                        "/api/job/auth/ping",
                        "/api/job/ping",
                        "/api/job/health"
                );
    }
}
