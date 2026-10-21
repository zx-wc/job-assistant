package com.nuist.agent.job.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 求职业务视图 DTO（成员 B/C/D：前后端接口契约）。
 * 前端直接消费这些结构，字段名与 docs/03-接口规范.md 保持一致。
 */
public final class ViewDtos {

    private ViewDtos() {
    }

    /** 通用分页结果 */
    @Data
    public static class PageResult<T> {
        private List<T> list = new ArrayList<>();
        private long total;
        private int page;
        private int size;
        private int pages;
        private String keyword;

        public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
            PageResult<T> result = new PageResult<>();
            result.setList(list == null ? new ArrayList<>() : list);
            result.setTotal(total);
            result.setPage(page);
            result.setSize(size);
            result.setPages(size <= 0 ? 0 : (int) Math.ceil((double) total / size));
            return result;
        }
    }

    /** 简历与岗位分析报告 */
    @Data
    public static class AnalysisVO {
        private Long id;
        private Long resumeId;
        private String resumeTitle;
        private Long positionId;
        private String positionTitle;
        private String company;
        private Integer matchScore;
        private String summary;
        private List<String> strengths = new ArrayList<>();
        private List<String> weaknesses = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private List<String> missingSkills = new ArrayList<>();
        private List<String> interviewFocus = new ArrayList<>();
        private Map<String, Integer> dimensionScores = new LinkedHashMap<>();
        private String model;
        private boolean degraded;
        private String createTime;
    }

    /** 面试题目 */
    @Data
    public static class QuestionVO {
        private Long id;
        private Integer questionNo;
        private String question;
        private String type;
        private List<String> expectedPoints = new ArrayList<>();
        private String answer;
        private String feedback;
        private Integer score;
        private Integer status;
        private boolean degraded;
    }

    /** 面试会话（含题目，可含报告） */
    @Data
    public static class SessionVO {
        private Long id;
        private String title;
        private String difficulty;
        private Integer status;
        private Integer totalQuestions;
        private Integer answeredCount;
        private Integer overallScore;
        private Long resumeId;
        private String resumeTitle;
        private Long positionId;
        private String positionTitle;
        private String company;
        private boolean degraded;
        private String createTime;
        private String endTime;
        private List<QuestionVO> questions = new ArrayList<>();
        private ReportVO feedback;
    }

    /** 单题作答结果 */
    @Data
    public static class AnswerResultVO {
        private Integer questionNo;
        private Integer score;
        private String comment;
        private List<String> highlights = new ArrayList<>();
        private List<String> improvements = new ArrayList<>();
        private int answeredCount;
        private int totalQuestions;
        private boolean allAnswered;
        private boolean degraded;
        private QuestionVO nextQuestion;
    }

    /** 面试反馈报告 */
    @Data
    public static class ReportVO {
        private Long sessionId;
        private Integer overallScore;
        private String summary;
        private List<String> strengths = new ArrayList<>();
        private List<String> weaknesses = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private Map<String, Integer> dimensionScores = new LinkedHashMap<>();
        private String model;
        private boolean degraded;
        private String createTime;
    }

    /** 概览页统计 */
    @Data
    public static class DashboardVO {
        private int resumeCount;
        private int positionCount;
        private int analysisCount;
        private int interviewCount;
        private int ongoingInterviewCount;
        private Integer latestMatchScore;
        private List<AnalysisVO> recentAnalyses = new ArrayList<>();
        private List<SessionVO> recentInterviews = new ArrayList<>();
    }
}
