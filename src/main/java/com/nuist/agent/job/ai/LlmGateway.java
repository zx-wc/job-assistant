package com.nuist.agent.job.ai;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * 大模型统一网关（成员 A：AI 集成核心）。
 *
 * <p>业务模块只依赖该接口，不直接接触 ChatClient / DashScope SDK；
 * 网关内部完成：模板渲染、超时控制、失败重试与备用模型切换、单用户限流、
 * 结果缓存、熔断降级、调用日志、结构化 JSON 解析。</p>
 */
public interface LlmGateway {

    /**
     * 调用大模型返回纯文本。
     *
     * @param scene     场景
     * @param variables 提示词模板变量
     * @param userPrompt 用户输入/指令
     * @return 文本结果
     * @throws LlmUnavailableException 模型不可用（调用方应走本地降级）
     */
    LlmTextResult chat(PromptScene scene, Map<String, Object> variables, String userPrompt);

    /**
     * 调用大模型并解析为结构化对象；解析失败会追加格式约束重试一次。
     *
     * @throws LlmUnavailableException 模型不可用或输出无法解析
     */
    <T> T chatJson(PromptScene scene, Map<String, Object> variables, String userPrompt, Class<T> type);

    /**
     * 调用大模型并解析为泛型结构（如 List）。
     */
    <T> T chatJson(PromptScene scene, Map<String, Object> variables, String userPrompt, TypeReference<T> type);

    /**
     * 与 {@link #chatJson} 相同，但同时返回实际使用的模型与缓存标记，供业务落库。
     */
    <T> LlmJsonResult<T> chatJsonWithMeta(PromptScene scene, Map<String, Object> variables,
                                          String userPrompt, Class<T> type);

    /** 网关当前是否可用（未熔断且已启用） */
    boolean available();
}
