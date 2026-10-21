package com.nuist.agent.job.ai;

/**
 * 结构化 AI 结果 + 调用元信息（成员 A：统一网关）。
 *
 * @param value  解析后的业务对象
 * @param model  实际使用的模型
 * @param cached 是否命中缓存
 * @param <T>    业务对象类型
 */
public record LlmJsonResult<T>(T value, String model, boolean cached) {
}
