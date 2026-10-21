package com.nuist.agent.job.controller;

import com.nuist.agent.exception.GlobalExceptionHandler;
import com.nuist.agent.job.auth.LoginUser;
import com.nuist.agent.job.auth.UserContextHolder;
import com.nuist.agent.job.dto.ViewDtos;
import com.nuist.agent.job.interview.InterviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模拟面试接口测试（成员 C 接口 + 成员 E 接口测试）。
 */
class JobInterviewControllerTest {

    private InterviewService interviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        interviewService = mock(InterviewService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new JobInterviewController(interviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
        UserContextHolder.set(new LoginUser(1L, "jobuser", "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void start_shouldReturnSession() throws Exception {
        when(interviewService.start(eq(1L), any())).thenReturn(session());

        mockMvc.perform(post("/api/job/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":2,\"difficulty\":\"HARD\",\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.dataMap.session.id").value(100))
                .andExpect(jsonPath("$.dataMap.session.questions[0].questionNo").value(1));
    }

    @Test
    void start_withoutPosition_shouldReturnParameterError() throws Exception {
        mockMvc.perform(post("/api/job/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.msg").value("positionId 不能为空"));
    }

    @Test
    void answer_shouldPassQuestionNoAndAnswer() throws Exception {
        ViewDtos.AnswerResultVO result = new ViewDtos.AnswerResultVO();
        result.setQuestionNo(1);
        result.setScore(85);
        result.setComment("不错");
        when(interviewService.answer(1L, 100L, 1, "我的回答")).thenReturn(result);

        mockMvc.perform(post("/api/job/interviews/100/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionNo\":1,\"answer\":\"我的回答\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.result.score").value(85));

        verify(interviewService).answer(1L, 100L, 1, "我的回答");
    }

    @Test
    void answer_withoutBody_shouldReturnParameterError() throws Exception {
        mockMvc.perform(post("/api/job/interviews/100/answers").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100));
    }

    @Test
    void followUp_shouldReturnUpdatedSession() throws Exception {
        when(interviewService.followUp(eq(1L), eq(100L), eq(1), any())).thenReturn(session());

        mockMvc.perform(post("/api/job/interviews/100/follow-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionNo\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void followUp_withoutQuestionNo_shouldReturnParameterError() throws Exception {
        mockMvc.perform(post("/api/job/interviews/100/follow-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100));
    }

    @Test
    void finish_shouldReturnReport() throws Exception {
        ViewDtos.ReportVO report = new ViewDtos.ReportVO();
        report.setSessionId(100L);
        report.setOverallScore(82);
        report.setSummary("总结");
        when(interviewService.finish(1L, 100L)).thenReturn(report);

        mockMvc.perform(post("/api/job/interviews/100/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.report.overallScore").value(82));
    }

    @Test
    void report_shouldReturnFeedback() throws Exception {
        ViewDtos.ReportVO report = new ViewDtos.ReportVO();
        report.setSessionId(100L);
        report.setOverallScore(70);
        when(interviewService.report(1L, 100L)).thenReturn(report);

        mockMvc.perform(get("/api/job/interviews/100/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.report.overallScore").value(70));
    }

    @Test
    void page_shouldReturnRecords() throws Exception {
        when(interviewService.page(1L, null, 1, 10))
                .thenReturn(ViewDtos.PageResult.of(List.of(session()), 1, 1, 10));

        mockMvc.perform(get("/api/job/interviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.page.total").value(1))
                .andExpect(jsonPath("$.dataMap.page.list[0].title").value("模拟面试"));
    }

    @Test
    void page_withStatusFilter() throws Exception {
        when(interviewService.page(1L, 1, 1, 10)).thenReturn(ViewDtos.PageResult.of(List.of(), 0, 1, 10));

        mockMvc.perform(get("/api/job/interviews").param("status", "1"))
                .andExpect(status().isOk());
        verify(interviewService).page(1L, 1, 1, 10);
    }

    @Test
    void detail_shouldReturnSession() throws Exception {
        when(interviewService.detail(1L, 100L)).thenReturn(session());

        mockMvc.perform(get("/api/job/interviews/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.session.id").value(100));
    }

    @Test
    void withoutLogin_shouldReturnUnauthorized() throws Exception {
        UserContextHolder.clear();
        mockMvc.perform(get("/api/job/interviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    private ViewDtos.SessionVO session() {
        ViewDtos.SessionVO vo = new ViewDtos.SessionVO();
        vo.setId(100L);
        vo.setTitle("模拟面试");
        vo.setDifficulty("NORMAL");
        vo.setStatus(0);
        vo.setTotalQuestions(1);
        vo.setAnsweredCount(0);
        ViewDtos.QuestionVO question = new ViewDtos.QuestionVO();
        question.setQuestionNo(1);
        question.setQuestion("请介绍项目");
        question.setType("项目");
        vo.setQuestions(List.of(question));
        return vo;
    }
}
