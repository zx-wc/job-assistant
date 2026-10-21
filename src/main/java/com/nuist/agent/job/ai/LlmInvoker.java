package com.nuist.agent.job.ai;

/**
 * 大模型单次调用抽象（成员 A：统一网关可替换/可切换）。
 *
 * <p>把"怎么调模型"与"网关的重试、超时、限流、降级、缓存"解耦：
 * 默认实现是 DashScope（通义千问），单元测试可注入假实现，
 * 未来切换到 DeepSeek / 豆包只需新增一个实现类并改配置。</p>
 */
public interface LlmInvoker {

    /**
     * 执行一次模型调用。
     *
     * @throws Exception 网络异常、鉴权失败、模型报错等
     */
    LlmRawResponse invoke(LlmRequest request) throws Exception;
}
