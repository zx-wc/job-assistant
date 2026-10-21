package com.nuist.agent.job.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.InterviewFeedback;
import com.nuist.agent.entity.InterviewQuestion;
import com.nuist.agent.entity.InterviewSession;
import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.ai.AiResults;
import com.nuist.agent.job.ai.LlmGateway;
import com.nuist.agent.job.ai.LlmJsonResult;
import com.nuist.agent.job.ai.LlmTextResult;
import com.nuist.agent.job.ai.LlmUnavailableException;
import com.nuist.agent.job.ai.PromptScene;
import com.nuist.agent.job.common.JsonHelper;
import com.nuist.agent.job.config.JobProperties;
import com.nuist.agent.job.dto.JobDtos;
import com.nuist.agent.job.dto.ViewDtos;
import com.nuist.agent.job.rag.JobVectorService;
import com.nuist.agent.mapper.InterviewFeedbackMapper;
import com.nuist.agent.mapper.InterviewQuestionMapper;
import com.nuist.agent.mapper.InterviewSessionMapper;
import com.nuist.agent.mapper.JobPositionMapper;
import com.nuist.agent.mapper.JobResumeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模拟面试服务测试（成员 C 模块 + 成员 E 测试）。
 * 覆盖：出题（AI / 降级）、逐题作答（评分归一化、重复作答、已结束）、追问、
 * 结束生成报告（AI / 降级 / 无作答）、报告查询、记录分页。
 */
class InterviewServiceImplTest {

    private InterviewSessionMapper sessionMapper;
    private InterviewQuestionMapper questionMapper;
    private InterviewFeedbackMapper feedbackMapper;
    private JobPositionMapper positionMapper;
    private JobResumeMapper resumeMapper;
    private FakeGateway gateway;
    private InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(InterviewSessionMapper.class);
        questionMapper = mock(InterviewQuestionMapper.class);
        feedbackMapper = mock(InterviewFeedbackMapper.class);
        positionMapper = mock(JobPositionMapper.class);
        resumeMapper = mock(JobResumeMapper.class);
        gateway = new FakeGateway();
        JobVectorService vectorService = mock(JobVectorService.class);
        when(vectorService.buildContext(any(), any(), any(), any(), anyInt())).thenReturn("(无)");
        service = new InterviewServiceImpl(sessionMapper, questionMapper, feedbackMapper, resumeMapper,
                positionMapper, gateway, new JsonHelper(new ObjectMapper()), new FallbackInterviewCoach(),
                new JobProperties(), vectorService);

        when(positionMapper.selectById(2L, 1L)).thenReturn(position());
        when(sessionMapper.insert(any(InterviewSession.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, InterviewSession.class).setId(100L);
            return 1;
        });
        when(sessionMapper.incrementAnswered(anyLong(), anyLong())).thenReturn(1);
        when(sessionMapper.finish(anyLong(), anyLong(), any())).thenReturn(1);
        when(sessionMapper.updateTotalQuestions(anyLong(), anyLong(), anyInt())).thenReturn(1);
        when(questionMapper.batchInsert(any())).thenReturn(2);
        when(questionMapper.insert(any(InterviewQuestion.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, InterviewQuestion.class).setId(999L);
            return 1;
        });
        when(feedbackMapper.insert(any(InterviewFeedback.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, InterviewFeedback.class).setId(500L);
            return 1;
        });
    }

    // ==================== 出题 ====================

    @Test
    void start_aiSuccess_shouldInsertSessionAndQuestions() {
        gateway.responses.put(PromptScene.INTERVIEW_QUESTION, questionList(3));
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(3, 0));
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of(question(1, 0), question(2, 0), question(3, 0)));

        JobDtos.InterviewStartRequest request = new JobDtos.InterviewStartRequest();
        request.setPositionId(2L);
        request.setQuestionCount(3);
        request.setDifficulty("hard");

        ViewDtos.SessionVO vo = service.start(1L, request);

        assertNotNull(vo);
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        InterviewSession saved = sessionCaptor.getValue();
        assertEquals(3, saved.getTotalQuestions());
        assertEquals("HARD", saved.getDifficulty());
        assertEquals("某公司-Java 后端 模拟面试", saved.getTitle());
        assertEquals("qwen-plus", saved.getModel());
        assertEquals(0, saved.getDegraded());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InterviewQuestion>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(questionMapper).batchInsert(listCaptor.capture());
        List<InterviewQuestion> savedQuestions = listCaptor.getValue();
        assertEquals(3, savedQuestions.size());
        assertEquals(1, savedQuestions.get(0).getQuestionNo());
        assertEquals(2, savedQuestions.get(1).getQuestionNo());
        assertEquals(3, savedQuestions.get(2).getQuestionNo());
        assertEquals("项目", savedQuestions.get(1).getQuestionType(), "题型应被归一化");
    }

    @Test
    void start_questionCountClampedToMax() {
        gateway.responses.put(PromptScene.INTERVIEW_QUESTION, questionList(20));
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(12, 0));
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of());

        JobDtos.InterviewStartRequest request = new JobDtos.InterviewStartRequest();
        request.setPositionId(2L);
        request.setQuestionCount(99);

        service.start(1L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InterviewQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionMapper).batchInsert(captor.capture());
        assertEquals(12, captor.getValue().size(), "题目数量应受配置上限约束");
    }

    @Test
    void start_aiUnavailable_shouldGenerateFallbackQuestions() {
        gateway.error = new LlmUnavailableException("超时");
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(3, 0));
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of());

        JobDtos.InterviewStartRequest request = new JobDtos.InterviewStartRequest();
        request.setPositionId(2L);
        request.setQuestionCount(3);

        service.start(1L, request);

        ArgumentCaptor<InterviewSession> captor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getDegraded());
        assertEquals("local-fallback", captor.getValue().getModel());
        assertEquals(3, captor.getValue().getTotalQuestions());
    }

    @Test
    void start_positionMissing_shouldThrow() {
        JobDtos.InterviewStartRequest request = new JobDtos.InterviewStartRequest();
        request.setPositionId(88L);
        BusinessException error = assertThrows(BusinessException.class, () -> service.start(1L, request));
        assertEquals(ErrorCode.POSITION_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void start_nullRequest_shouldThrow() {
        assertEquals(ErrorCode.PARAMETER_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.start(1L, null)).getCode());
    }

    // ==================== 作答 ====================

    @Test
    void answer_success_shouldScoreAndMoveNext() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 1));
        when(questionMapper.selectByQuestionNo(100L, 1)).thenReturn(question(1, 0));
        when(questionMapper.countAnswered(100L)).thenReturn(2);
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of(question(1, 1), question(2, 1)));
        AiResults.AnswerEvalResult eval = new AiResults.AnswerEvalResult();
        eval.setScore(120);
        eval.setComment("回答不错");
        eval.setHighlights(new ArrayList<>(List.of("结构清晰")));
        eval.setImprovements(new ArrayList<>(List.of("补充数据")));
        gateway.responses.put(PromptScene.INTERVIEW_EVALUATE, eval);

        ViewDtos.AnswerResultVO vo = service.answer(1L, 100L, 1, "我的回答");

        assertEquals(100, vo.getScore(), "分数应归一化到 0-100");
        assertEquals("回答不错", vo.getComment());
        assertEquals(2, vo.getAnsweredCount());
        assertTrue(vo.isAllAnswered());
        assertNull(vo.getNextQuestion());
        assertFalse(vo.isDegraded());
        verify(questionMapper).updateAnswer(eq(100L), eq(1), eq("我的回答"), anyString(), eq(100), eq(0));
        verify(sessionMapper).incrementAnswered(100L, 1L);
    }

    @Test
    void answer_success_shouldReturnNextQuestion() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(3, 1));
        when(questionMapper.selectByQuestionNo(100L, 2)).thenReturn(question(2, 0));
        when(questionMapper.countAnswered(100L)).thenReturn(2);
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of(question(1, 1), question(2, 1), question(3, 0)));
        gateway.responses.put(PromptScene.INTERVIEW_EVALUATE, new AiResults.AnswerEvalResult());

        ViewDtos.AnswerResultVO vo = service.answer(1L, 100L, 2, "回答");

        assertNotNull(vo.getNextQuestion());
        assertEquals(3, vo.getNextQuestion().getQuestionNo());
        assertFalse(vo.isAllAnswered());
    }

    @Test
    void answer_aiUnavailable_shouldFallbackScore() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 1));
        when(questionMapper.selectByQuestionNo(100L, 1)).thenReturn(question(1, 0));
        when(questionMapper.countAnswered(100L)).thenReturn(1);
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of(question(1, 1)));
        gateway.error = new LlmUnavailableException("超时");

        ViewDtos.AnswerResultVO vo = service.answer(1L, 100L, 1, "我做了订单模块，接口 P99 从 320ms 降到 90ms");

        assertTrue(vo.isDegraded());
        assertTrue(vo.getScore() >= 20 && vo.getScore() <= 92);
        verify(questionMapper).updateAnswer(eq(100L), eq(1), anyString(), anyString(), anyInt(), eq(1));
    }

    @Test
    void answer_duplicate_shouldThrow() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 1));
        when(questionMapper.selectByQuestionNo(100L, 1)).thenReturn(question(1, 1));
        BusinessException error = assertThrows(BusinessException.class, () -> service.answer(1L, 100L, 1, "回答"));
        assertEquals(ErrorCode.QUESTION_ANSWERED.getCode(), error.getCode());
    }

    @Test
    void answer_finishedSession_shouldThrow() {
        InterviewSession finished = session(2, 2);
        finished.setStatus(1);
        when(sessionMapper.selectById(100L, 1L)).thenReturn(finished);
        BusinessException error = assertThrows(BusinessException.class, () -> service.answer(1L, 100L, 1, "回答"));
        assertEquals(ErrorCode.INTERVIEW_FINISHED.getCode(), error.getCode());
    }

    @Test
    void answer_blankAnswer_shouldThrow() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 0));
        BusinessException error = assertThrows(BusinessException.class, () -> service.answer(1L, 100L, 1, "  "));
        assertEquals(ErrorCode.PARAMETER_ERROR.getCode(), error.getCode());
    }

    @Test
    void answer_questionMissing_shouldThrow() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 0));
        when(questionMapper.selectByQuestionNo(100L, 5)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.answer(1L, 100L, 5, "回答"));
        assertEquals(ErrorCode.QUESTION_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void answer_nullQuestionNo_shouldThrow() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 0));
        BusinessException error = assertThrows(BusinessException.class, () -> service.answer(1L, 100L, null, "回答"));
        assertEquals(ErrorCode.QUESTION_NOT_FOUND.getCode(), error.getCode());
    }

    // ==================== 追问 ====================

    @Test
    void followUp_shouldAppendQuestionAndUpdateTotal() {
        InterviewQuestion answered = question(1, 1);
        answered.setAnswer("我的回答内容");
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 1));
        when(questionMapper.selectByQuestionNo(100L, 1)).thenReturn(answered);
        when(questionMapper.selectMaxQuestionNo(100L)).thenReturn(2);
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of(question(1, 1), question(3, 0)));
        AiResults.FollowUpResult followUp = new AiResults.FollowUpResult();
        followUp.setQuestion("能具体说说你是怎么定位慢 SQL 的吗？");
        followUp.setExpectedPoints(new ArrayList<>(List.of("定位手段", "优化效果")));
        gateway.responses.put(PromptScene.INTERVIEW_FOLLOW_UP, followUp);

        service.followUp(1L, 100L, 1, null);

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getQuestionNo());
        assertEquals("追问", captor.getValue().getQuestionType());
        assertTrue(captor.getValue().getQuestion().contains("慢 SQL"));
        verify(sessionMapper).updateTotalQuestions(100L, 1L, 3);
    }

    @Test
    void followUp_withoutAnswer_shouldThrow() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 0));
        when(questionMapper.selectByQuestionNo(100L, 1)).thenReturn(question(1, 0));
        BusinessException error = assertThrows(BusinessException.class, () -> service.followUp(1L, 100L, 1, null));
        assertEquals(ErrorCode.PARAMETER_ERROR.getCode(), error.getCode());
    }

    @Test
    void followUp_aiUnavailable_shouldUseLocalQuestion() {
        InterviewQuestion answered = question(1, 1);
        answered.setAnswer("回答");
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 1));
        when(questionMapper.selectByQuestionNo(100L, 1)).thenReturn(answered);
        when(questionMapper.selectMaxQuestionNo(100L)).thenReturn(1);
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of());
        gateway.error = new LlmUnavailableException("超时");

        service.followUp(1L, 100L, 1, null);

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).insert(captor.capture());
        assertTrue(captor.getValue().getQuestion().contains("具体细节"));
        assertEquals(1, captor.getValue().getDegraded());
    }

    // ==================== 报告 ====================

    @Test
    void finish_aiSuccess_shouldPersistFeedback() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 2));
        when(feedbackMapper.selectBySessionId(100L)).thenReturn(null);
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of(scored(1, 80), scored(2, 60)));
        AiResults.InterviewReportResult report = new AiResults.InterviewReportResult();
        report.setOverallScore(88);
        report.setSummary("整体表现不错");
        report.setStrengths(new ArrayList<>(List.of("项目描述清晰")));
        report.setWeaknesses(new ArrayList<>(List.of("深度不足")));
        report.setSuggestions(new ArrayList<>(List.of("补充原理")));
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("专业知识", 85);
        report.setDimensionScores(dimensions);
        gateway.responses.put(PromptScene.INTERVIEW_REPORT, report);

        ViewDtos.ReportVO vo = service.finish(1L, 100L);

        assertEquals(88, vo.getOverallScore());
        assertEquals(List.of("项目描述清晰"), vo.getStrengths());
        assertEquals(85, vo.getDimensionScores().get("专业知识"));
        verify(sessionMapper).finish(100L, 1L, 88);
        ArgumentCaptor<InterviewFeedback> captor = ArgumentCaptor.forClass(InterviewFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertEquals(88, captor.getValue().getOverallScore());
        assertEquals(0, captor.getValue().getDegraded());
    }

    @Test
    void finish_existingFeedback_shouldReturnWithoutInsert() {
        InterviewFeedback feedback = new InterviewFeedback();
        feedback.setSessionId(100L);
        feedback.setOverallScore(70);
        feedback.setSummary("已生成");
        feedback.setStrengths("[]");
        feedback.setWeaknesses("[]");
        feedback.setSuggestions("[]");
        feedback.setDimensionScores("{}");
        feedback.setCreateTime(LocalDateTime.now());
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 2));
        when(feedbackMapper.selectBySessionId(100L)).thenReturn(feedback);

        ViewDtos.ReportVO vo = service.finish(1L, 100L);

        assertEquals(70, vo.getOverallScore());
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    void finish_aiUnavailable_shouldFallback() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 2));
        when(feedbackMapper.selectBySessionId(100L)).thenReturn(null);
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of(scored(1, 80), scored(2, 60)));
        gateway.error = new LlmUnavailableException("超时");

        ViewDtos.ReportVO vo = service.finish(1L, 100L);

        assertEquals(70, vo.getOverallScore(), "降级报告应取各题平均分");
        assertTrue(vo.isDegraded());
        assertEquals("local-fallback", vo.getModel());
    }

    @Test
    void finish_noAnsweredQuestion_shouldThrow() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 0));
        when(feedbackMapper.selectBySessionId(100L)).thenReturn(null);
        when(questionMapper.selectBySessionId(100L)).thenReturn(List.of(question(1, 0), question(2, 0)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.finish(1L, 100L));
        assertEquals(ErrorCode.PARAMETER_ERROR.getCode(), error.getCode());
    }

    @Test
    void report_missing_shouldThrow() {
        when(sessionMapper.selectById(100L, 1L)).thenReturn(session(2, 2));
        when(feedbackMapper.selectBySessionId(100L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.report(1L, 100L));
        assertEquals(ErrorCode.FEEDBACK_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void detail_missing_shouldThrow() {
        when(sessionMapper.selectById(404L, 1L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.detail(1L, 404L));
        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), error.getCode());
    }

    // ==================== 记录 ====================

    @Test
    void page_shouldMapSessions() {
        when(sessionMapper.selectByPage(1L, null, 0, 10)).thenReturn(List.of(session(2, 1)));
        when(sessionMapper.countByUser(1L, null)).thenReturn(1);

        ViewDtos.PageResult<ViewDtos.SessionVO> page = service.page(1L, null, 1, 10);

        assertEquals(1, page.getList().size());
        assertEquals(1, page.getTotal());
    }

    @Test
    void countByUser_shouldDelegate() {
        when(sessionMapper.countByUser(1L, null)).thenReturn(5);
        when(sessionMapper.countOngoing(1L)).thenReturn(2);
        assertEquals(5, service.countByUser(1L));
        assertEquals(2, service.countOngoing(1L));
    }

    // ==================== 辅助 ====================

    private InterviewSession session(int total, int answered) {
        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setUserId(1L);
        session.setPositionId(2L);
        session.setTitle("某公司-Java 后端 模拟面试");
        session.setPositionTitle("Java 后端");
        session.setCompany("某公司");
        session.setDifficulty("NORMAL");
        session.setTotalQuestions(total);
        session.setAnsweredCount(answered);
        session.setStatus(0);
        session.setDegraded(0);
        session.setCreateTime(LocalDateTime.now());
        return session;
    }

    private InterviewQuestion question(int no, int status) {
        InterviewQuestion question = new InterviewQuestion();
        question.setId((long) no);
        question.setSessionId(100L);
        question.setQuestionNo(no);
        question.setQuestion("请介绍第" + no + "题");
        question.setQuestionType(no == 1 ? "技能" : "项目");
        question.setExpectedPoints("[\"要点1\"]");
        question.setStatus(status);
        return question;
    }

    private InterviewQuestion scored(int no, int score) {
        InterviewQuestion question = question(no, 1);
        question.setScore(score);
        question.setAnswer("回答内容");
        return question;
    }

    private AiResults.QuestionListResult questionList(int count) {
        AiResults.QuestionListResult result = new AiResults.QuestionListResult();
        List<AiResults.QuestionItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            AiResults.QuestionItem item = new AiResults.QuestionItem();
            item.setQuestion("AI 生成的第 " + i + " 题");
            item.setType(i == 1 ? "技能" : "项目经历");
            item.setExpectedPoints(new ArrayList<>(List.of("要点A", "要点B")));
            items.add(item);
        }
        result.setQuestions(items);
        return result;
    }

    private JobPosition position() {
        JobPosition position = new JobPosition();
        position.setId(2L);
        position.setUserId(1L);
        position.setPosition("Java 后端");
        position.setCompany("某公司");
        position.setJobDesc("负责后端开发");
        position.setRequirements("熟悉 Java、Redis");
        return position;
    }

    /** 假网关：按场景返回预设结果或抛异常 */
    private static class FakeGateway implements LlmGateway {
        final Map<PromptScene, Object> responses = new EnumMap<>(PromptScene.class);
        RuntimeException error;
        PromptScene lastScene;
        Map<String, Object> lastVariables;

        @Override
        public LlmTextResult chat(PromptScene scene, Map<String, Object> variables, String userPrompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T chatJson(PromptScene scene, Map<String, Object> variables, String userPrompt, Class<T> type) {
            return chatJsonWithMeta(scene, variables, userPrompt, type).value();
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
            Object value = responses.get(scene);
            if (value == null) {
                throw new UnsupportedOperationException("未配置场景:" + scene);
            }
            return new LlmJsonResult<>((T) value, "qwen-plus", false);
        }

        @Override
        public boolean available() {
            return error == null;
        }
    }
}
