package com.nuist.agent.zhangxu.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 大模型密钥可用性判断（成员 A：AI 基础设施）。
 *
 * <p>application.yml 中 {@code spring.ai.dashscope.api-key} 未配置时会落为占位值，
 * 以保证 Spring AI 自动配置能正常启动；这里统一判断密钥是否真的可用，
 * 让 AI 网关与向量检索在未配置密钥时直接走本地降级，避免无意义的远程调用与报错。</p>
 */
@Component
public class AiAvailability {

    /** application.yml 中密钥未配置时使用的占位值 */
    public static final String PLACEHOLDER_API_KEY = "sk-not-configured";

    private final String apiKey;

    public AiAvailability(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /** 是否配置了真实密钥 */
    public boolean configured() {
        return StringUtils.hasText(apiKey) && !PLACEHOLDER_API_KEY.equals(apiKey);
    }

    /** 原始密钥（仅用于脱敏展示，禁止下发前端明文） */
    public String apiKey() {
        return apiKey;
    }
}
