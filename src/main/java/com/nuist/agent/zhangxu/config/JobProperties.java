package com.nuist.agent.zhangxu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能求职辅助系统配置（成员 A：公共组件 / 网关 / 安全）。
 * 对应 application.yml 中的 job.* 前缀。
 */
@Data
@ConfigurationProperties(prefix = "job")
public class JobProperties {

    /** 安全与权限 */
    private Security security = new Security();

    /** 大模型网关 */
    private Ai ai = new Ai();

    /** 简历相关限制 */
    private Resume resume = new Resume();

    /** 模拟面试相关限制 */
    private Interview interview = new Interview();

    /** 文件上传限制 */
    private Upload upload = new Upload();

    @Data
    public static class Security {
        /** 登录令牌有效期（小时），默认 7 天 */
        private long tokenTtlHours = 168;
        /** 令牌续期阈值：剩余有效期低于该小时数时自动续期 */
        private long refreshThresholdHours = 24;
        /** 管理员用户名（逗号分隔），登录时自动授予 ADMIN 角色 */
        private List<String> adminUsernames = new ArrayList<>(List.of("admin"));
    }

    @Data
    public static class Ai {
        /** 是否启用真实大模型调用；false 时全部走本地降级策略（便于离线答辩演示） */
        private boolean enabled = true;
        /** 主模型 */
        private String model = "qwen-plus";
        /** 备用模型列表：主模型连续失败后按顺序切换 */
        private List<String> fallbackModels = new ArrayList<>(List.of("qwen-turbo"));
        /** 单次调用超时（毫秒） */
        private long timeoutMs = 20_000L;
        /** 失败重试次数（不含首次调用） */
        private int maxRetries = 2;
        /** 重试退避基数（毫秒），第 n 次重试等待 n * base */
        private long retryBackoffMs = 300L;
        /** 单用户限流：窗口内最大请求数 */
        private int rateLimitPerMinute = 20;
        /** 限流窗口（秒） */
        private long rateLimitWindowSeconds = 60L;
        /** 结果缓存有效期（秒），0 表示不缓存 */
        private long cacheTtlSeconds = 600L;
        /** 结果缓存最大条目数 */
        private int cacheMaxEntries = 200;
        /** 连续失败多少次后熔断 */
        private int circuitFailureThreshold = 3;
        /** 熔断持续时长（毫秒） */
        private long circuitOpenMs = 60_000L;
        /** 采样温度 */
        private Double temperature = 0.4D;
        /** 单次请求的用户输入最大字符数（超出截断，避免超额 token） */
        private int maxInputChars = 8000;
    }

    @Data
    public static class Resume {
        /** 简历正文最大长度 */
        private int maxContentLength = 20_000;
        /** 列表默认分页大小 */
        private int defaultPageSize = 10;
    }

    @Data
    public static class Interview {
        /** 默认题目数量 */
        private int defaultQuestionCount = 5;
        /** 题目数量上限 */
        private int maxQuestionCount = 12;
        /** 单题作答最小长度（低于该值提示补充细节） */
        private int minAnswerLength = 10;
    }

    @Data
    public static class Upload {
        /** 上传文本文件最大字节数，默认 1MB */
        private long maxFileSize = 1_048_576L;
    }
}
