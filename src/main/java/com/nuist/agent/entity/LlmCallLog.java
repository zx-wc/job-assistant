package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大模型调用日志实体（成员 A：AI 统一网关可观测性）。
 * 对应表 t_llm_call_log：每次调用（含重试后的最终结果、缓存命中、降级）落一条记录。
 */
@Data
public class LlmCallLog {

    /** 主键 */
    private Long id;

    /** 调用用户 */
    private Long userId;

    /** 业务场景（PromptScene.code） */
    private String scene;

    /** 实际调用的模型 */
    private String model;

    /** 输入 token */
    private Integer promptTokens;

    /** 输出 token */
    private Integer completionTokens;

    /** 总 token */
    private Integer totalTokens;

    /** 耗时毫秒 */
    private Long durationMs;

    /** 重试次数 */
    private Integer retryCount;

    /** 1 命中结果缓存 */
    private Integer cached;

    /** 1 成功 / 0 失败 */
    private Integer success;

    /** 1 已降级到本地策略 */
    private Integer degraded;

    /** 失败原因（已脱敏截断） */
    private String errorMsg;

    /** 调用时间 */
    private LocalDateTime createTime;
}
