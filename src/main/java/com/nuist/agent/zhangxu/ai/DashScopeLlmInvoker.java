package com.nuist.agent.zhangxu.ai;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * DashScope（通义千问）调用实现（成员 A：AI 网关的默认 Provider）。
 *
 * <p>说明：这里刻意不使用容器中既有的 ChatClient Bean（它绑定了电商工具与记忆 Advisor），
 * 而是基于 ChatModel 单独构建一个"干净"的 ChatClient，避免求职业务被工具调用与历史会话干扰。</p>
 */
@Component
public class DashScopeLlmInvoker implements LlmInvoker {

    private static final Logger log = LoggerFactory.getLogger(DashScopeLlmInvoker.class);

    private final ChatClient chatClient;

    public DashScopeLlmInvoker(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public LlmRawResponse invoke(LlmRequest request) {
        long start = System.currentTimeMillis();
        double temperature = request.temperature() == null ? 0.4D : request.temperature();
        ChatResponse response = chatClient.prompt()
                .system(request.systemPrompt())
                .user(request.userPrompt())
                .options(DashScopeChatOptions.builder()
                        .withModel(request.model())
                        .withTemperature(temperature)
                        .build())
                .call()
                .chatResponse();
        long duration = System.currentTimeMillis() - start;
        String text = "";
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            text = response.getResult().getOutput().getText();
        }
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;
        if (response != null && response.getMetadata() != null) {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();
            }
        }
        String model = response != null && response.getMetadata() != null && response.getMetadata().getModel() != null
                ? response.getMetadata().getModel() : request.model();
        if (model == null || model.isBlank()) {
            model = request.model();
        }
        log.debug("DashScope 调用完成:scene={},model={},耗时={}ms,输出长度={}",
                request.scene().getCode(), model, duration, text == null ? 0 : text.length());
        return new LlmRawResponse(text == null ? "" : text, model, promptTokens, completionTokens, totalTokens, duration);
    }
}
