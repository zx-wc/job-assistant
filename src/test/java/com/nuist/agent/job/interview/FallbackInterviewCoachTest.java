package com.nuist.agent.job.interview;

import com.nuist.agent.entity.InterviewQuestion;
import com.nuist.agent.entity.InterviewSession;
import com.nuist.agent.zhangxu.ai.AiResults;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地降级面试教练测试（成员 C 降级策略 + 成员 E 测试）。
 */
class FallbackInterviewCoachTest {

    private final FallbackInterviewCoach coach = new FallbackInterviewCoach();

    @Test
    void generateQuestions_shouldMatchRequestedCount() {
        AiResults.QuestionListResult result = coach.generateQuestions(session(), List.of("Redis", "MySQL"), 4);
        assertEquals(4, result.getQuestions().size());
        result.getQuestions().forEach(item -> {
            assertFalse(item.getQuestion().isBlank());
            assertFalse(item.getExpectedPoints().isEmpty());
            assertTrue(List.of("技能", "项目", "行为", "岗位匹配").contains(item.getType()));
        });
    }

    @Test
    void generateQuestions_withoutKeywords_shouldStillWork() {
        AiResults.QuestionListResult result = coach.generateQuestions(session(), List.of(), 3);
        assertEquals(3, result.getQuestions().size());
        assertTrue(result.getQuestions().get(0).getQuestion().contains("项目经历")
                || result.getQuestions().get(0).getQuestion().contains("岗位"));
    }

    @Test
    void evaluate_shortAnswer_shouldScoreLow() {
        AiResults.AnswerEvalResult result = coach.evaluate("介绍项目", "做过一个项目", List.of("背景", "结果"));
        assertTrue(result.getScore() <= 55, "过短回答得分应偏低，实际=" + result.getScore());
        assertTrue(result.getComment().contains("本地规则评分"));
        assertFalse(result.getImprovements().isEmpty());
    }

    @Test
    void evaluate_detailedAnswer_shouldScoreHigher() {
        String answer = "首先交代背景：校园交易平台订单模块由我负责；"
                + "任务是把接口 P99 从 320ms 降到 100ms 以内；"
                + "行动上我增加了联合索引并引入 Redis 缓存热点商品，最后压测结果 P99 90ms，QPS 提升到 1200。";
        AiResults.AnswerEvalResult shortResult = coach.evaluate("介绍项目", "做过项目", List.of("背景", "结果"));
        AiResults.AnswerEvalResult longResult = coach.evaluate("介绍项目", answer, List.of("背景", "结果"));

        assertTrue(longResult.getScore() > shortResult.getScore());
        assertTrue(longResult.getScore() >= 60);
        assertFalse(longResult.getHighlights().isEmpty());
    }

    @Test
    void evaluate_emptyAnswer_shouldNotThrow() {
        AiResults.AnswerEvalResult result = coach.evaluate("题目", null, List.of("要点"));
        assertTrue(result.getScore() >= 20 && result.getScore() <= 92);
    }

    @Test
    void report_shouldAggregateScores() {
        List<InterviewQuestion> questions = new ArrayList<>();
        questions.add(question(1, "技能", 80));
        questions.add(question(2, "项目", 60));
        AiResults.InterviewReportResult report = coach.report(session(), questions);

        assertEquals(70, report.getOverallScore());
        assertEquals(4, report.getDimensionScores().size());
         assertFalse(report.getStrengths().isEmpty());
        assertFalse(report.getWeaknesses().isEmpty());
        assertFalse(report.getSuggestions().isEmpty());
        assertTrue(report.getSummary().contains("本地规则评分"));
    }

    @Test
    void report_noQuestions_shouldReturnZero() {
        AiResults.InterviewReportResult report = coach.report(session(), List.of());
        assertEquals(0, report.getOverallScore());
        assertFalse(report.getSuggestions().isEmpty());
    }

    @Test
    void report_unansweredQuestion_shouldNotCountInAverage() {
        List<InterviewQuestion> questions = new ArrayList<>();
        questions.add(question(1, "技能", 90));
        questions.add(question(2, "技能", null));
        AiResults.InterviewReportResult report = coach.report(session(), questions);
        assertEquals(90, report.getOverallScore(), "未作答题目不应拉低平均分");
        assertTrue(report.getSummary().contains("已作答 1"));
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setTitle("某公司-Java 后端 模拟面试");
        session.setDifficulty("NORMAL");
        return session;
    }

    private InterviewQuestion question(int no, String type, Integer score) {
        InterviewQuestion question = new InterviewQuestion();
        question.setQuestionNo(no);
        question.setQuestionType(type);
        question.setScore(score);
        question.setStatus(score == null ? 0 : 1);
        return question;
    }
}
