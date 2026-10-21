package com.nuist.agent.zhangxu.ai;

import com.nuist.agent.zhangxu.common.ErrorCode;
import com.nuist.agent.zhangxu.entity.LlmCallLog;
import com.nuist.agent.zhangxu.entity.LlmPromptTemplate;
import com.nuist.agent.zhangxu.exception.BusinessException;
import com.nuist.agent.zhangxu.config.JobProperties;
import com.nuist.agent.zhangxu.dto.JobDtos;
import com.nuist.agent.zhangxu.dto.ViewDtos;
import com.nuist.agent.zhangxu.mapper.LlmCallLogMapper;
import com.nuist.agent.zhangxu.mapper.LlmPromptTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 网关管理与可观测性（成员 A）。
 *
 * <p>提供：提示词模板在线编辑与还原、网关运行参数（密钥脱敏）、
 * 今日调用统计、按场景分布、近 7 天趋势、调用日志分页。</p>
 */
@Service
public class AiAdminService {

    private final JobProperties jobProperties;
    private final PromptTemplateService promptTemplateService;
    private final LlmPromptTemplateMapper promptTemplateMapper;
    private final LlmCallLogMapper callLogMapper;
    private final LlmGateway llmGateway;
    private final AiAvailability aiAvailability;

    public AiAdminService(JobProperties jobProperties,
                          PromptTemplateService promptTemplateService,
                          LlmPromptTemplateMapper promptTemplateMapper,
                          LlmCallLogMapper callLogMapper,
                          LlmGateway llmGateway,
                          AiAvailability aiAvailability) {
        this.jobProperties = jobProperties;
        this.promptTemplateService = promptTemplateService;
        this.promptTemplateMapper = promptTemplateMapper;
        this.callLogMapper = callLogMapper;
        this.llmGateway = llmGateway;
        this.aiAvailability = aiAvailability;
    }

    /** 全部场景及其模板来源、占位符、是否被数据库覆盖 */
    public List<Map<String, Object>> scenes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PromptScene scene : PromptScene.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", scene.getCode());
            item.put("title", scene.getTitle());
            item.put("source", promptTemplateService.source(scene));
            item.put("placeholders", new ArrayList<>(promptTemplateService.placeholders(scene)));
            item.put("content", promptTemplateService.template(scene).content());
            LlmPromptTemplate dbTemplate = promptTemplateMapper.selectByScene(scene.getCode());
            item.put("dbConfigured", dbTemplate != null);
            item.put("updatedTime", dbTemplate == null || dbTemplate.getUpdateTime() == null
                    ? "" : dbTemplate.getUpdateTime().toString());
            result.add(item);
        }
        return result;
    }

    /** 数据库中的模板列表 */
    public List<LlmPromptTemplate> templates() {
        return promptTemplateMapper.selectAll();
    }

    /** 保存（新增/覆盖）模板 */
    public void saveTemplate(String sceneCode, JobDtos.PromptTemplateSaveRequest request) {
        PromptScene scene = PromptScene.of(sceneCode);
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "模板内容不能为空");
        }
        LlmPromptTemplate template = new LlmPromptTemplate();
        template.setScene(scene.getCode());
        template.setName(StringUtils.hasText(request.getName()) ? request.getName().trim() : scene.getTitle());
        template.setContent(request.getContent());
        template.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        template.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        promptTemplateMapper.upsert(template);
        promptTemplateService.evictAll();
    }

    /** 删除数据库模板，回到 classpath 内置模板 */
    public void resetTemplate(String sceneCode) {
        PromptScene scene = PromptScene.of(sceneCode);
        promptTemplateMapper.deleteByScene(scene.getCode());
        promptTemplateService.evictAll();
    }

    /** 网关配置（密钥脱敏，绝不返回明文） */
    public Map<String, Object> config() {
        JobProperties.Ai ai = jobProperties.getAi();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", ai.isEnabled());
        config.put("model", ai.getModel());
        config.put("fallbackModels", ai.getFallbackModels());
        config.put("timeoutMs", ai.getTimeoutMs());
        config.put("maxRetries", ai.getMaxRetries());
        config.put("rateLimitPerMinute", ai.getRateLimitPerMinute());
        config.put("rateLimitWindowSeconds", ai.getRateLimitWindowSeconds());
        config.put("cacheTtlSeconds", ai.getCacheTtlSeconds());
        config.put("circuitFailureThreshold", ai.getCircuitFailureThreshold());
        config.put("circuitOpenMs", ai.getCircuitOpenMs());
        config.put("apiKeyConfigured", aiAvailability.configured());
        config.put("apiKeyMasked", aiAvailability.configured() ? maskKey(aiAvailability.apiKey()) : "");
        config.put("available", llmGateway.available());
        return config;
    }

    /** 今日调用统计 + 按场景分布 + 近 7 天趋势 */
    public Map<String, Object> statistics() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> stats = new LinkedHashMap<>();
        int total = callLogMapper.countSince(todayStart);
        int success = callLogMapper.countSuccessSince(todayStart);
        stats.put("todayTotal", total);
        stats.put("todaySuccess", success);
        stats.put("todayDegraded", callLogMapper.countDegradedSince(todayStart));
        stats.put("todayCached", callLogMapper.countCachedSince(todayStart));
        stats.put("todayFail", Math.max(0, total - success));
        stats.put("successRate", total == 0 ? 0 : Math.round(success * 100.0 / total));
        stats.put("avgDurationMs", callLogMapper.avgDurationSince(todayStart));
        stats.put("todayTokens", callLogMapper.sumTokensSince(todayStart));
        stats.put("allTotal", callLogMapper.count());
        stats.put("byScene", callLogMapper.countBySceneSince(todayStart));
        stats.put("daily", callLogMapper.selectDailyStats(7));
        stats.put("available", llmGateway.available());
        stats.put("model", jobProperties.getAi().getModel());
        return stats;
    }

    /** 调用日志分页 */
    public ViewDtos.PageResult<LlmCallLog> logs(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        int offset = (safePage - 1) * safeSize;
        List<LlmCallLog> list = callLogMapper.selectByPage(offset, safeSize);
        return ViewDtos.PageResult.of(list, callLogMapper.count(), safePage, safeSize);
    }

    private String maskKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        if (key.length() <= 12) {
            return "****";
        }
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }
}
