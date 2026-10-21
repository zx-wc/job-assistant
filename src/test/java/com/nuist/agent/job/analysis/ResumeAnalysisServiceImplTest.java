package com.nuist.agent.job.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuist.agent.zhangxu.common.ErrorCode;
import com.nuist.agent.entity.JobAnalysis;
import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.zhangxu.exception.BusinessException;
import com.nuist.agent.zhangxu.ai.AiResults;
import com.nuist.agent.zhangxu.ai.LlmGateway;
import com.nuist.agent.zhangxu.ai.LlmJsonResult;
import com.nuist.agent.zhangxu.ai.LlmTextResult;
import com.nuist.agent.zhangxu.ai.LlmUnavailableException;
import com.nuist.agent.zhangxu.ai.PromptScene;
import com.nuist.agent.zhangxu.common.JsonHelper;
import com.nuist.agent.zhangxu.dto.ViewDtos;
import com.nuist.agent.zhangxu.rag.JobVectorService;
import com.nuist.agent.mapper.JobAnalysisMapper;
import com.nuist.agent.mapper.JobPositionMapper;
import com.nuist.agent.mapper.JobResumeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 简历与岗位分析服务测试（成员 B 模块 + 成员 E 测试）。
 * 覆盖：AI 成功落库（结构化字段 + 分数归一化）、AI 不可用降级、关闭 AI 降级、
 * 归属校验（越权/不存在）、详情/分页/删除。
 */
class ResumeAnalysisServiceImplTest {

    private JobResumeMapper resumeMapper;
    private JobPositionMapper positionMapper;
    private JobAnalysisMapper analysisMapper;
    private FakeGateway gateway;
    private ResumeAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        resumeMapper = mock(JobResumeMapper.class);
        positionMapper = mock(JobPositionMapper.class);
        analysisMapper = mock(JobAnalysisMapper.class);
        gateway = new FakeGateway();
        JobVectorService vectorService = mock(JobVectorService.class);
        when(vectorService.buildContext(any(), any(), any(), any(), anyInt())).thenReturn("(无)");
        service = new ResumeAnalysisServiceImpl(resumeMapper, positionMapper, analysisMapper, gateway,
                new JsonHelper(new ObjectMapper()), new AnalysisFallbackAnalyzer(), vectorService);
        when(resumeMapper.selectById(1L, 1L)).thenReturn(resume());
        when(positionMapper.selectById(2L, 1L)).thenReturn(position());
        when(analysisMapper.insert(any(JobAnalysis.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, JobAnalysis.class).setId(77L);
            return 1;
        });
    }

    @Test
    void analyze_success_shouldPersistStructuredResult() {
        AiResults.ResumeAnalysisResult ai = new AiResults.ResumeAnalysisResult();
        ai.setMatchScore(150);
        ai.setSummary("整体不错");
        ai.setStrengths(new ArrayList<>(List.of("项目量化清晰")));
        ai.setWeaknesses(new ArrayList<>(List.of("缺少高并发经验")));
        ai.setSuggestions(new ArrayList<>(List.of("补充缓存一致性实践")));
        ai.setMissingSkills(new ArrayList<>(List.of("Kafka")));
        ai.setInterviewFocus(new ArrayList<>(List.of("项目难点")));
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("技能匹配", 80);
        ai.setDimensionScores(dimensions);
        gateway.result = ai;

        ViewDtos.AnalysisVO vo = service.analyze(1L, 1L, 2L);

        assertEquals(100, vo.getMatchScore(), "分数应被归一化到 0-100");
        assertFalse(vo.isDegraded());
        assertEquals("qwen-plus", vo.getModel());
        assertEquals("简历标题", vo.getResumeTitle());
        assertEquals("Java 后端", vo.getPositionTitle());
        assertEquals(List.of("项目量化清晰"), vo.getStrengths());
        assertEquals(80, vo.getDimensionScores().get("技能匹配"));

        ArgumentCaptor<JobAnalysis> captor = ArgumentCaptor.forClass(JobAnalysis.class);
        verify(analysisMapper).insert(captor.capture());
        JobAnalysis entity = captor.getValue();
        assertEquals(0, entity.getDegraded());
        assertEquals("[\"Kafka\"]", entity.getMissingSkills());
        assertTrue(entity.getDimensionScores().contains("技能匹配"));
    }

    @Test
    void analyze_aiUnavailable_shouldFallbackWithDegradedFlag() {
        gateway.error = new LlmUnavailableException("超时");

        ViewDtos.AnalysisVO vo = service.analyze(1L, 1L, 2L);

        assertTrue(vo.isDegraded());
        assertEquals("local-fallback", vo.getModel());
        assertTrue(vo.getMatchScore() > 0);
        assertFalse(vo.getSuggestions().isEmpty());
        verify(analysisMapper).insert(any(JobAnalysis.class));
    }

    @Test
    void analyze_aiDisabled_shouldFallback() {
        gateway.error = new BusinessException(ErrorCode.AI_DISABLED);

        ViewDtos.AnalysisVO vo = service.analyze(1L, 1L, 2L);

        assertTrue(vo.isDegraded());
    }

    @Test
    void analyze_rateLimited_shouldPropagate() {
        gateway.error = new BusinessException(ErrorCode.AI_RATE_LIMITED);
        BusinessException error = assertThrows(BusinessException.class, () -> service.analyze(1L, 1L, 2L));
        assertEquals(ErrorCode.AI_RATE_LIMITED.getCode(), error.getCode());
    }

    @Test
    void analyze_resumeMissing_shouldThrow() {
        when(resumeMapper.selectById(9L, 1L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.analyze(1L, 9L, 2L));
        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void analyze_positionMissing_shouldThrow() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.analyze(1L, 1L, 99L));
        assertEquals(ErrorCode.POSITION_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void analyze_nullIds_shouldThrow() {
        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(),
                assertThrows(BusinessException.class, () -> service.analyze(1L, null, 2L)).getCode());
        assertEquals(ErrorCode.POSITION_NOT_FOUND.getCode(),
                assertThrows(BusinessException.class, () -> service.analyze(1L, 1L, null)).getCode());
    }

    @Test
    void analyze_variablesShouldCarryPositionContext() {
        gateway.result = new AiResults.ResumeAnalysisResult();
        service.analyze(1L, 1L, 2L);
        assertEquals(PromptScene.RESUME_ANALYSIS, gateway.lastScene);
        assertEquals("Java 后端", gateway.lastVariables.get("positionTitle"));
        assertEquals("某公司", gateway.lastVariables.get("company"));
        assertTrue(String.valueOf(gateway.lastVariables.get("resume")).contains("项目经历"));
    }

    @Test
    void detail_missing_shouldThrow() {
        when(analysisMapper.selectById(5L, 1L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.detail(1L, 5L));
        assertEquals(ErrorCode.ANALYSIS_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void detail_shouldParseJsonFields() {
        JobAnalysis entity = analysisEntity();
        when(analysisMapper.selectById(5L, 1L)).thenReturn(entity);

        ViewDtos.AnalysisVO vo = service.detail(1L, 5L);

        assertEquals(List.of("优势1"), vo.getStrengths());
        assertEquals(70, vo.getDimensionScores().get("技能匹配"));
        assertEquals("2026-01-01 10:00:00", vo.getCreateTime());
    }

    @Test
    void page_shouldMapEntities() {
        when(analysisMapper.selectByPage(1L, 0, 10)).thenReturn(List.of(analysisEntity()));
        when(analysisMapper.countByUser(1L)).thenReturn(1);

        ViewDtos.PageResult<ViewDtos.AnalysisVO> page = service.page(1L, 1, 10);

        assertEquals(1, page.getList().size());
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getPages());
    }

    @Test
    void delete_missing_shouldThrow() {
        when(analysisMapper.deleteById(5L, 1L)).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.delete(1L, 5L));
    }

    @Test
    void delete_success_shouldCallMapper() {
        when(analysisMapper.deleteById(5L, 1L)).thenReturn(1);
        service.delete(1L, 5L);
        verify(analysisMapper).deleteById(5L, 1L);
    }

    @Test
    void countByUser_shouldDelegate() {
        when(analysisMapper.countByUser(1L)).thenReturn(3);
        assertEquals(3, service.countByUser(1L));
    }

    private JobAnalysis analysisEntity() {
        JobAnalysis entity = new JobAnalysis();
        entity.setId(5L);
        entity.setUserId(1L);
        entity.setResumeId(1L);
        entity.setPositionId(2L);
        entity.setResumeTitle("简历标题");
        entity.setPositionTitle("Java 后端");
        entity.setCompany("某公司");
        entity.setMatchScore(70);
        entity.setSummary("总结");
        entity.setStrengths("[\"优势1\"]");
        entity.setWeaknesses("[\"短板1\"]");
        entity.setSuggestions("[\"建议1\"]");
        entity.setMissingSkills("[\"Kafka\"]");
        entity.setInterviewFocus("[\"追问\"]");
        entity.setDimensionScores("{\"技能匹配\": 70}");
        entity.setModel("qwen-plus");
        entity.setDegraded(0);
        entity.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
        return entity;
    }

    private JobResume resume() {
        JobResume resume = new JobResume();
        resume.setId(1L);
        resume.setUserId(1L);
        resume.setTitle("简历标题");
        resume.setTargetPosition("Java 后端");
        resume.setContent("项目经历：订单模块");
        return resume;
    }

    private JobPosition position() {
        JobPosition position = new JobPosition();
        position.setId(2L);
        position.setUserId(1L);
        position.setPosition("Java 后端");
        position.setCompany("某公司");
        position.setJobDesc("负责后端开发");
        position.setRequirements("熟悉 Java");
        return position;
    }

    /** 假网关：可配置返回或异常 */
    private static class FakeGateway implements LlmGateway {
        AiResults.ResumeAnalysisResult result;
        RuntimeException error;
        PromptScene lastScene;
        Map<String, Object> lastVariables;

        @Override
        public LlmTextResult chat(PromptScene scene, Map<String, Object> variables, String userPrompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T chatJson(PromptScene scene, Map<String, Object> variables, String userPrompt, Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T chatJson(PromptScene scene, Map<String, Object> variables, String userPrompt,
                             TypeReference<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> LlmJsonResult<T> chatJsonWithMeta(PromptScene scene, Map<String, Object> variables,
                                                     String userPrompt, Class<T> type) {
            lastScene = scene;
            lastVariables = variables;
            if (error != null) {
                throw error;
            }
            return new LlmJsonResult<>((T) result, "qwen-plus", false);
        }

        @Override
        public boolean available() {
            return error == null;
        }
    }
}
