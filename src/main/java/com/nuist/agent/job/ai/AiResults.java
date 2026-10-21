package com.nuist.agent.job.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务结构化输出模型（成员 A/B/C 共用，与提示词中的 JSON 契约一一对应）。
 *
 * <p>全部字段允许缺省：大模型偶尔漏字段时不应让业务失败，
 * 由业务层做兜底（空列表、0 分）并标记为降级。</p>
 */
public final class AiResults {

    private AiResults() {
    }

    /** 简历与岗位分析结果 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResumeAnalysisResult {
        private Integer matchScore;
        private String summary;
        private List<String> strengths = new ArrayList<>();
        private List<String> weaknesses = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private List<String> missingSkills = new ArrayList<>();
        private List<String> interviewFocus = new ArrayList<>();
        private Map<String, Integer> dimensionScores = new LinkedHashMap<>();
    }

    /** 单道面试题 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionItem {
        private String question;
        private String type;
        private List<String> expectedPoints = new ArrayList<>();
    }

    /** 出题结果 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionListResult {
        private List<QuestionItem> questions = new ArrayList<>();
    }

    /** 单题评分结果 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnswerEvalResult {
        private Integer score;
        private String comment;
        private List<String> highlights = new ArrayList<>();
        private List<String> improvements = new ArrayList<>();
    }

    /** 面试反馈报告结果 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InterviewReportResult {
        private Integer overallScore;
        private String summary;
        private List<String> strengths = new ArrayList<>();
        private List<String> weaknesses = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private Map<String, Integer> dimensionScores = new LinkedHashMap<>();
    }

    /** 追问结果 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FollowUpResult {
        private String question;
        private List<String> expectedPoints = new ArrayList<>();
    }
}
