package com.nuist.agent.zhangxu.ai;

/**
 * 一次大模型调用的原始返回（成员 A：统一网关）。
 *
 * @param text             模型输出文本
 * @param model            实际响应模型
 * @param promptTokens     输入 token（可能为 null）
 * @param completionTokens 输出 token（可能为 null）
 * @param totalTokens      总 token（可能为 null）
 * @param durationMs       调用耗时（毫秒）
 */
public record LlmRawResponse(String text, String model, Integer promptTokens,
                             Integer completionTokens, Integer totalTokens, long durationMs) {

    public static LlmRawResponse ofText(String text, String model, long durationMs) {
        return new LlmRawResponse(text, model, null, null, null, durationMs);
    }
}
