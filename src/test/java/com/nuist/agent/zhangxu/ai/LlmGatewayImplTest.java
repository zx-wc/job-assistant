package com.nuist.agent.zhangxu.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuist.agent.zhangxu.common.ErrorCode;
import com.nuist.agent.zhangxu.entity.LlmCallLog;
import com.nuist.agent.zhangxu.exception.BusinessException;
import com.nuist.agent.zhangxu.config.JobProperties;
import com.nuist.agent.zhangxu.mapper.LlmCallLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 统一网关测试（成员 E：核心组件单元测试）。
 *
 * <p>用可编排的假 LlmInvoker 覆盖：正常返回、缓存命中、失败重试、备用模型切换、
 * 超时、熔断、限流、关闭开关、结构化解析与格式重试、调用日志落库。</p>
 */
class LlmGatewayImplTest {

    private JobProperties properties;
    private FakeInvoker invoker;
    private LlmCallLogMapper logMapper;
    private ContentSafetyFilter safetyFilter;
    private ExecutorService executor;
    private LlmGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        properties = new JobProperties();
        properties.getAi().setTimeoutMs(800);
        properties.getAi().setMaxRetries(2);
        properties.getAi().setRetryBackoffMs(1);
        properties.getAi().setRateLimitPerMinute(100);
        properties.getAi().setCacheTtlSeconds(60);
        properties.getAi().setCircuitFailureThreshold(3);

        invoker = new FakeInvoker();
        logMapper = mock(LlmCallLogMapper.class);
        safetyFilter = mock(ContentSafetyFilter.class);
        when(safetyFilter.maskOutput(anyString())).thenAnswer(call -> call.getArgument(0));
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        when(promptTemplateService.resolve(any(), any())).thenReturn("系统提示词");

        executor = Executors.newFixedThreadPool(2);
        gateway = new LlmGatewayImpl(properties, invoker, new AiAvailability("test-key"), promptTemplateService,
                logMapper, safetyFilter, new SlidingWindowRateLimiter(), new LlmResultCache(20, 60_000),
                executor, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // ==================== 正常路径 ====================

    @Test
    void chat_success_shouldReturnTextAndLog() {
        invoker.plan("这是一段 AI 回答");

        LlmTextResult result = gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "分析一下");

        assertEquals("这是一段 AI 回答", result.text());
        assertFalse(result.cached());
        assertEquals(0, result.retryCount());
        assertEquals(1, invoker.calls);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(logMapper).insert(captor.capture());
        LlmCallLog log = captor.getValue();
        assertEquals("resume-analysis", log.getScene());
        assertEquals(1, log.getSuccess());
        assertEquals(0, log.getCached());
        assertEquals(0, log.getDegraded());
        assertEquals(0, log.getRetryCount());
    }

    @Test
    void chat_shouldUseSceneTemplateAndConfiguredModel() {
        invoker.plan("ok");
        gateway.chat(PromptScene.INTERVIEW_QUESTION, Map.of("questionCount", 5), "出题");

        assertEquals("qwen-plus", invoker.lastRequest.model());
        assertEquals(PromptScene.INTERVIEW_QUESTION, invoker.lastRequest.scene());
        assertNotNull(invoker.lastRequest.systemPrompt());
        assertEquals(properties.getAi().getTemperature(), invoker.lastRequest.temperature());
    }

    @Test
    void chat_secondSameCall_shouldHitCache() {
        invoker.plan("缓存结果");
        gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "同一段输入");
        LlmTextResult second = gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "同一段输入");

        assertTrue(second.cached());
        assertEquals(1, invoker.calls);
        assertEquals("缓存结果", second.text());
    }

    @Test
    void chat_differentInput_shouldNotUseCache() {
        invoker.plan("结果");
        gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "输入A");
        gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "输入B");
        assertEquals(2, invoker.calls);
    }

    @Test
    void chat_outputSensitive_shouldBeMasked() {
        when(safetyFilter.maskOutput(anyString())).thenReturn("已脱敏内容");
        invoker.plan("违规内容");
        assertEquals("已脱敏内容", gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "x").text());
    }

    // ==================== 重试与备用模型 ====================

    @Test
    void chat_firstFailThenSuccess_shouldRetry() {
        invoker.plan(new IllegalStateException("网络抖动"), "第二次成功");

        LlmTextResult result = gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "x");

        assertEquals("第二次成功", result.text());
        assertEquals(1, result.retryCount());
        assertEquals(2, invoker.calls);
    }

    @Test
    void chat_allAttemptsFail_shouldThrowAndLogFailure() {
        invoker.plan(new IllegalStateException("服务不可用"));

        assertThrows(LlmUnavailableException.class,
                () -> gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "x"));
        assertEquals(3, invoker.calls);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(logMapper, atLeastOnce()).insert(captor.capture());
        LlmCallLog log = captor.getValue();
        assertEquals(0, log.getSuccess());
        assertEquals(1, log.getDegraded());
    }

    @Test
    void chat_retryShouldSwitchToFallbackModel() {
        invoker.plan(new IllegalStateException("主模型失败"));

        assertThrows(LlmUnavailableException.class,
                () -> gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "x"));

        assertEquals(List.of("qwen-plus", "qwen-turbo", "qwen-plus"), invoker.models);
    }

    @Test
    void chat_emptyResponse_shouldBeTreatedAsFailure() {
        invoker.plan("   ");
        assertThrows(LlmUnavailableException.class,
                () -> gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "x"));
    }

    // ==================== 超时与熔断 ====================

    @Test
    void chat_timeout_shouldThrowAfterRetries() {
        properties.getAi().setTimeoutMs(30);
        properties.getAi().setMaxRetries(1);
        invoker.delayMillis = 300;

        assertThrows(LlmUnavailableException.class,
                () -> gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "x"));
        assertEquals(2, invoker.calls);
    }

    @Test
    void chat_circuitOpen_shouldShortCircuit() {
        properties.getAi().setCircuitFailureThreshold(1);
        properties.getAi().setMaxRetries(0);
        properties.getAi().setCircuitOpenMs(10_000);
        invoker.plan(new IllegalStateException("挂了"));

        assertThrows(LlmUnavailableException.class,
                () -> gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "第一次"));
        int callsAfterFirst = invoker.calls;
        assertFalse(gateway.available());

        assertThrows(LlmUnavailableException.class,
                () -> gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "第二次"));
        assertEquals(callsAfterFirst, invoker.calls, "熔断期间不应再调用模型");
    }

    // ==================== 限流与开关 ====================

    @Test
    void chat_rateLimited_shouldThrowBusinessException() {
        properties.getAi().setRateLimitPerMinute(1);
        invoker.plan("ok");

        gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "第一次");
        // 第二次换了输入，绕过缓存，触发限流
        BusinessException error = assertThrows(BusinessException.class,
                () -> gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "第二次"));
        assertEquals(ErrorCode.AI_RATE_LIMITED.getCode(), error.getCode());
        assertEquals(1, invoker.calls);
    }

    @Test
    void chat_disabled_shouldNotCallModel() {
        properties.getAi().setEnabled(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> gateway.chat(PromptScene.RESUME_ANALYSIS, Map.of(), "x"));
        assertEquals(ErrorCode.AI_DISABLED.getCode(), error.getCode());
        assertEquals(0, invoker.calls);
        verify(logMapper, never()).insert(any());
    }

    // ==================== 结构化输出 ====================

    @Test
    void chatJson_validJson_shouldParse() {
        invoker.plan("{\"score\": 85, \"comment\": \"不错\"}");
        AiResults.AnswerEvalResult result = gateway.chatJson(PromptScene.INTERVIEW_EVALUATE,
                Map.of(), "评分", AiResults.AnswerEvalResult.class);
        assertEquals(85, result.getScore());
        assertEquals("不错", result.getComment());
    }

    @Test
    void chatJson_markdownFence_shouldStillParse() {
        invoker.plan("```json\n{\"score\": 70}\n```");
        assertEquals(70, gateway.chatJson(PromptScene.INTERVIEW_EVALUATE, Map.of(), "评分",
                AiResults.AnswerEvalResult.class).getScore());
    }

    @Test
    void chatJson_invalidThenValid_shouldRetryWithFormatHint() {
        invoker.plan("这不是 JSON", "{\"score\": 66}");
        LlmJsonResult<AiResults.AnswerEvalResult> result = gateway.chatJsonWithMeta(
                PromptScene.INTERVIEW_EVALUATE, Map.of(), "评分", AiResults.AnswerEvalResult.class);

        assertEquals(66, result.value().getScore());
        assertTrue(invoker.lastRequest.userPrompt().contains("JSON"), "重试时应追加格式约束");
        assertEquals(2, invoker.calls);
    }

    @Test
    void chatJson_invalidTwice_shouldThrow() {
        invoker.plan("仍然不是 JSON");
        assertThrows(LlmUnavailableException.class, () -> gateway.chatJson(
                PromptScene.INTERVIEW_EVALUATE, Map.of(), "评分", AiResults.AnswerEvalResult.class));
    }

    @Test
    void chatJsonWithMeta_shouldExposeModel() {
        invoker.plan("{\"matchScore\": 61}");
        LlmJsonResult<AiResults.ResumeAnalysisResult> result = gateway.chatJsonWithMeta(
                PromptScene.RESUME_ANALYSIS, Map.of(), "分析", AiResults.ResumeAnalysisResult.class);
        assertEquals("qwen-plus", result.model());
        assertEquals(61, result.value().getMatchScore());
    }

    @Test
    void available_shouldBeTrueWhenHealthy() {
        assertTrue(gateway.available());
    }

    // ==================== 测试替身 ====================

    /** 可编排的假模型调用器：plan() 依次返回结果或抛异常 */
    private static class FakeInvoker implements LlmInvoker {
        final List<Object> plan = new ArrayList<>();
        final List<String> models = new ArrayList<>();
        int calls;
        long delayMillis;
        LlmRequest lastRequest;

        FakeInvoker plan(Object... items) {
            plan.addAll(List.of(items));
            return this;
        }

        @Override
        public LlmRawResponse invoke(LlmRequest request) throws Exception {
            calls++;
            models.add(request.model());
            lastRequest = request;
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            Object item = plan.isEmpty() ? "默认输出" : (plan.size() > 1 ? plan.remove(0) : plan.get(0));
            if (item instanceof Exception exception) {
                throw exception;
            }
            return LlmRawResponse.ofText(String.valueOf(item), request.model(), 8L);
        }
    }
}
