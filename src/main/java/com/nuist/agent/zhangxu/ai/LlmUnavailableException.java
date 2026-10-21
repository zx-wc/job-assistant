package com.nuist.agent.zhangxu.ai;

/**
 * AI 不可用异常（成员 A：网关降级信号）。
 *
 * <p>业务层捕获该异常后切换到本地规则降级策略（<b>绝不会把异常直接抛给前端</b>），
 * 保证大模型不可用时系统仍可用。</p>
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
