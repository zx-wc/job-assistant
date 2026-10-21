package com.nuist.agent.job.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.LlmCallLog;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.auth.UserContextHolder;
import com.nuist.agent.job.config.JobProperties;
import com.nuist.agent.mapper.LlmCallLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大模型统一网关实现（成员 A：AI 集成核心）。
 *
 * <p>一次调用要过 6 道关：
 * <ol>
 *   <li><b>开关</b>：job.ai.enabled=false 时直接拒绝，全部业务走本地降级（离线答辩可演示）；</li>
 *   <li><b>缓存</b>：相同场景 + 相同输入在 TTL 内直接复用结果；</li>
 *   <li><b>限流</b>：按用户滑动窗口限流，防止刷接口耗尽额度；</li>
 *   <li><b>超时</b>：调用放到独立线程池，Future.get 超时即取消，避免请求线程被拖死；</li>
 *   <li><b>重试 + 备用模型</b>：失败按退避策略重试，并按主模型→备用模型顺序切换；</li>
 *   <li><b>熔断 + 降级</b>：连续失败达到阈值后短路一段时间，抛 {@link LlmUnavailableException}
 *       让业务走本地规则策略；所有结果写入 t_llm_call_log 便于排查。</li>
 * </ol>
 */
@Service
public class LlmGatewayImpl implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGatewayImpl.class);

    /** JSON 解析失败后的补充约束（只追加一次） */
    private static final String JSON_ONLY_SUFFIX = "\n\n【重要】上一次输出不符合格式要求。"
            + "本次必须只输出一个合法 JSON 对象：不要 Markdown 代码块，不要任何解释文字，字段名与示例完全一致。";

    private final JobProperties properties;
    private final LlmInvoker invoker;
    private final AiAvailability aiAvailability;
    private final PromptTemplateService promptTemplateService;
    private final LlmCallLogMapper callLogMapper;
    private final ContentSafetyFilter safetyFilter;
    private final SlidingWindowRateLimiter rateLimiter;
    private final LlmResultCache cache;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;

    /** 连续失败次数（用于熔断） */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    /** 熔断截止时间戳（毫秒） */
    private final AtomicLong circuitOpenUntil = new AtomicLong(0L);

    public LlmGatewayImpl(JobProperties properties,
                          LlmInvoker invoker,
                          AiAvailability aiAvailability,
                          PromptTemplateService promptTemplateService,
                          LlmCallLogMapper callLogMapper,
                          ContentSafetyFilter safetyFilter,
                          SlidingWindowRateLimiter rateLimiter,
                          LlmResultCache cache,
                          @Qualifier("jobAiExecutor") ExecutorService executor,
                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.invoker = invoker;
        this.aiAvailability = aiAvailability;
        this.promptTemplateService = promptTemplateService;
        this.callLogMapper = callLogMapper;
        this.safetyFilter = safetyFilter;
        this.rateLimiter = rateLimiter;
        this.cache = cache;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmTextResult chat(PromptScene scene, Map<String, Object> variables, String userPrompt) {
        JobProperties.Ai ai = properties.getAi();
        // 未配置真实密钥时直接降级，避免每个请求都去远程鉴权失败后重试
        if (!ai.isEnabled() || !aiAvailability.configured()) {
            throw new BusinessException(ErrorCode.AI_DISABLED);
        }
        Long userId = UserContextHolder.getUserId();
        String prompt = truncate(userPrompt, ai.getMaxInputChars());
        String systemPrompt = promptTemplateService.resolve(scene, variables);

        // 1. 结果缓存
        String cacheKey = LlmResultCache.buildKey(scene.getCode(), systemPrompt + "\u0001" + prompt);
        Optional<String> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            log.info("AI 结果缓存命中:scene={},userId={}", scene.getCode(), userId);
            writeLog(userId, scene, ai.getModel(), null, null, null, 0, true, true, 0, 0L, "");
            return new LlmTextResult(cached.get(), ai.getModel(), true, 0);
        }

        // 2. 限流
        checkRateLimit(userId);

        // 3. 熔断
        long now = System.currentTimeMillis();
        if (now < circuitOpenUntil.get()) {
            long remain = circuitOpenUntil.get() - now;
            log.warn("AI 网关处于熔断状态,剩余 {} ms,scene={}", remain, scene.getCode());
            writeLog(userId, scene, ai.getModel(), null, null, null, 0, false, false, 1, 0L, "circuit-open");
            throw new LlmUnavailableException("AI 服务熔断中(" + remain + "ms)，请稍后再试");
        }

        // 4. 重试 + 备用模型
        List<String> models = modelChain(ai);
        int maxAttempts = Math.max(1, ai.getMaxRetries() + 1);
        Throwable lastError = null;
        long startAll = System.currentTimeMillis();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String model = models.get(attempt % models.size());
            try {
                LlmRawResponse raw = invokeOnce(scene, model, systemPrompt, prompt, ai.getTimeoutMs());
                String text = safetyFilter.maskOutput(raw.text());
                if (!StringUtils.hasText(text)) {
                    throw new LlmUnavailableException("模型返回内容为空");
                }
                // 成功：恢复熔断计数并写缓存
                consecutiveFailures.set(0);
                circuitOpenUntil.set(0L);
                cache.put(cacheKey, text);
                writeLog(userId, scene, raw.model(), raw.promptTokens(), raw.completionTokens(),
                        raw.totalTokens(), attempt, false, true, 0,
                        Math.max(raw.durationMs(), 0L), "");
                log.info("AI 调用成功:scene={},model={},重试={}", scene.getCode(), raw.model(), attempt);
                return new LlmTextResult(text, raw.model(), false, attempt);
            } catch (Exception e) {
                lastError = e;
                log.warn("AI 调用失败(第 {}/{} 次):scene={},model={},err={}",
                        attempt + 1, maxAttempts, scene.getCode(), model, rootMessage(e));
                if (attempt < maxAttempts - 1) {
                    sleep(ai.getRetryBackoffMs() * (attempt + 1));
                }
            }
        }

        // 5. 全部失败：熔断 + 上报降级信号
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= ai.getCircuitFailureThreshold()) {
            circuitOpenUntil.set(System.currentTimeMillis() + ai.getCircuitOpenMs());
            log.error("AI 网关连续失败 {} 次，进入熔断 {} ms", failures, ai.getCircuitOpenMs());
        }
        writeLog(userId, scene, models.get(0), null, null, null, maxAttempts - 1, false, false, 1,
                System.currentTimeMillis() - startAll, rootMessage(lastError));
        throw new LlmUnavailableException("大模型调用失败:" + rootMessage(lastError), lastError);
    }

    @Override
    public <T> T chatJson(PromptScene scene, Map<String, Object> variables, String userPrompt, Class<T> type) {
        return chatJsonWithMeta(scene, variables, userPrompt, type).value();
    }

    @Override
    public <T> T chatJson(PromptScene scene, Map<String, Object> variables, String userPrompt, TypeReference<T> type) {
        LlmTextResult text = chat(scene, variables, userPrompt);
        try {
            return JsonExtractor.parse(text.text(), type, objectMapper);
        } catch (LlmUnavailableException first) {
            log.warn("AI 输出不是合法 JSON，追加格式约束后重试一次:scene={},err={}", scene.getCode(), first.getMessage());
            String retryPrompt = truncate(userPrompt + JSON_ONLY_SUFFIX, properties.getAi().getMaxInputChars());
            String retryText = chat(scene, variables, retryPrompt).text();
            return JsonExtractor.parse(retryText, type, objectMapper);
        }
    }

    @Override
    public <T> LlmJsonResult<T> chatJsonWithMeta(PromptScene scene, Map<String, Object> variables,
                                                 String userPrompt, Class<T> type) {
        LlmTextResult text = chat(scene, variables, userPrompt);
        try {
            T value = JsonExtractor.parse(text.text(), type, objectMapper);
            return new LlmJsonResult<>(value, text.model(), text.cached());
        } catch (LlmUnavailableException first) {
            log.warn("AI 输出不是合法 JSON，追加格式约束后重试一次:scene={},err={}", scene.getCode(), first.getMessage());
            String retryPrompt = truncate(userPrompt + JSON_ONLY_SUFFIX, properties.getAi().getMaxInputChars());
            LlmTextResult retryText = chat(scene, variables, retryPrompt);
            T value = JsonExtractor.parse(retryText.text(), type, objectMapper);
            return new LlmJsonResult<>(value, retryText.model(), retryText.cached());
        }
    }

    @Override
    public boolean available() {
        return properties.getAi().isEnabled() && aiAvailability.configured()
                && System.currentTimeMillis() >= circuitOpenUntil.get();
    }

    /** 单次调用，带超时保护 */
    private LlmRawResponse invokeOnce(PromptScene scene, String model, String systemPrompt,
                                      String userPrompt, long timeoutMs) {
        LlmRequest request = new LlmRequest(scene, model, systemPrompt, userPrompt,
                properties.getAi().getTemperature());
        Future<LlmRawResponse> future = executor.submit(() -> invoker.invoke(request));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new LlmUnavailableException("模型调用超时(" + timeoutMs + "ms)");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("模型调用被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new LlmUnavailableException("模型调用失败:" + rootMessage(cause), cause);
        }
    }

    /** 限流：按用户维度滑动窗口 */
    private void checkRateLimit(Long userId) {
        JobProperties.Ai ai = properties.getAi();
        if (ai.getRateLimitPerMinute() <= 0) {
            return;
        }
        String key = "llm:" + (userId == null ? "guest" : userId);
        boolean allowed = rateLimiter.tryAcquire(key, ai.getRateLimitPerMinute(),
                ai.getRateLimitWindowSeconds() * 1000L);
        if (!allowed) {
            log.warn("AI 调用触发限流:{}", key);
            writeLog(userId, null, ai.getModel(), null, null, null, 0, false, false, 0, 0L, "rate-limited");
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }
    }

    /** 模型链：主模型 + 备用模型 */
    private List<String> modelChain(JobProperties.Ai ai) {
        List<String> models = new ArrayList<>();
        if (StringUtils.hasText(ai.getModel())) {
            models.add(ai.getModel().trim());
        }
        if (ai.getFallbackModels() != null) {
            for (String fallback : ai.getFallbackModels()) {
                if (StringUtils.hasText(fallback) && !models.contains(fallback.trim())) {
                    models.add(fallback.trim());
                }
            }
        }
        if (models.isEmpty()) {
            models.add("qwen-plus");
        }
        return models;
    }

    /** 写调用日志（失败不影响主流程；scene 为 null 表示限流等网关内部事件） */
    private void writeLog(Long userId, PromptScene scene, String model, Integer promptTokens,
                          Integer completionTokens, Integer totalTokens, int retryCount,
                          boolean cached, boolean success, int degraded, long durationMs, String errorMsg) {
        try {
            LlmCallLog callLog = new LlmCallLog();
            callLog.setUserId(userId);
            callLog.setScene(scene == null ? "gateway" : scene.getCode());
            callLog.setModel(model);
            callLog.setPromptTokens(promptTokens);
            callLog.setCompletionTokens(completionTokens);
            callLog.setTotalTokens(totalTokens);
            callLog.setRetryCount(retryCount);
            callLog.setCached(cached ? 1 : 0);
            callLog.setSuccess(success ? 1 : 0);
            callLog.setDegraded(degraded);
            callLog.setDurationMs(durationMs);
            callLog.setErrorMsg(truncate(errorMsg, 500));
            callLogMapper.insert(callLog);
        } catch (Exception e) {
            log.warn("写入 AI 调用日志失败:{}", e.getMessage());
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        log.warn("输入长度 {} 超过上限 {}，已截断", text.length(), maxChars);
        return text.substring(0, maxChars);
    }

    private String rootMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.hasText(message) ? message : current.getClass().getSimpleName();
    }
}
