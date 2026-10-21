package com.nuist.agent.zhangxu.ai;

/**
 * 一次大模型调用请求（成员 A：统一网关）。
 *
 * @param scene        场景
 * @param model        本次使用的模型（主模型或备用模型）
 * @param systemPrompt 已渲染好的系统提示词
 * @param userPrompt   用户输入（业务数据/指令）
 * @param temperature  采样温度
 */
public record LlmRequest(PromptScene scene, String model, String systemPrompt,
                         String userPrompt, Double temperature) {
}
