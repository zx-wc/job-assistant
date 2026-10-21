package com.nuist.agent.zhangxu.ai;

/**
 * 文本类 AI 调用结果（成员 A：统一网关）。
 *
 * @param text      模型输出文本
 * @param model     实际模型
 * @param cached    是否命中缓存
 * @param retryCount 本次调用经历的重试次数
 */
public record LlmTextResult(String text, String model, boolean cached, int retryCount) {
}
