package com.nuist.agent.job.analysis;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.zhangxu.ai.AiResults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地降级分析测试（成员 B 降级策略 + 成员 E 测试）。
 * 验证：关键词覆盖度计算、缺失技能识别、建议与维度评分始终可用。
 */
class AnalysisFallbackAnalyzerTest {

    private final AnalysisFallbackAnalyzer analyzer = new AnalysisFallbackAnalyzer();

    @Test
    void analyze_partialMatch_shouldReportMissingSkills() {
        JobResume resume = resume("""
                项目经历：使用 Java + Spring Boot + MyBatis 开发校园交易平台，MySQL 存储订单数据。
                接口 P99 从 320ms 降到 90ms。
                """);
        JobPosition position = position("熟悉 Java / Spring Boot / MySQL，了解 Redis 缓存与 Linux 常用命令");

        AiResults.ResumeAnalysisResult result = analyzer.analyze(resume, position);

        assertTrue(result.getMatchScore() > 0 && result.getMatchScore() <= 95);
        assertTrue(result.getMissingSkills().stream().anyMatch(skill -> skill.equalsIgnoreCase("Redis")
                || skill.equalsIgnoreCase("Linux")));
        assertFalse(result.getSuggestions().isEmpty());
        assertEquals(4, result.getDimensionScores().size());
        assertTrue(result.getSummary().contains("本地规则分析"));
    }

    @Test
    void analyze_fullMatch_shouldScoreHigher() {
        JobResume resume = resume("Java Spring Boot MySQL Redis Linux Docker 项目 QPS 1200 提升 3 倍 实习");
        JobPosition position = position("Java Spring Boot MySQL Redis Linux Docker");

        AiResults.ResumeAnalysisResult result = analyzer.analyze(resume, position);

        assertTrue(result.getMatchScore() >= 80, "完整覆盖关键词时匹配度应较高，实际=" + result.getMatchScore());
        assertTrue(result.getMissingSkills().isEmpty());
    }

    @Test
    void analyze_noKeywordInPosition_shouldGiveNeutralScore() {
        JobPosition position = new JobPosition();
        position.setId(3L);
        position.setPosition("行政助理");
        position.setJobDesc("负责日常行政事务");
        position.setRequirements("工作细致，有责任心");
        AiResults.ResumeAnalysisResult result = analyzer.analyze(resume("普通简历内容"), position);
        assertEquals(60, result.getMatchScore());
        assertTrue(result.getSummary().contains("中性评估"));
    }

    @Test
    void analyze_shortResume_shouldMentionContentLength() {
        AiResults.ResumeAnalysisResult result = analyzer.analyze(resume("Java"), position("Java MySQL Redis"));
        assertTrue(result.getWeaknesses().stream().anyMatch(text -> text.contains("偏短")));
    }

    @Test
    void analyze_noQuantifiedData_shouldSuggestQuantify() {
        AiResults.ResumeAnalysisResult result = analyzer.analyze(
                resume("负责订单模块开发，参与需求评审与接口设计，按时完成交付"),
                position("Java 后端"));
        assertTrue(result.getSuggestions().stream().anyMatch(text -> text.contains("量化")));
    }

    @Test
    void analyze_shouldAlwaysProduceFourDimensions() {
        AiResults.ResumeAnalysisResult result = analyzer.analyze(resume("内容"), position("要求"));
        assertTrue(result.getDimensionScores().containsKey("技能匹配"));
        assertTrue(result.getDimensionScores().containsKey("项目经验"));
        assertTrue(result.getDimensionScores().containsKey("岗位契合"));
        assertTrue(result.getDimensionScores().containsKey("表达呈现"));
        assertFalse(result.getInterviewFocus().isEmpty());
    }

    @Test
    void analyze_nullContent_shouldNotThrow() {
        JobResume resume = new JobResume();
        resume.setTitle("空简历");
        AiResults.ResumeAnalysisResult result = analyzer.analyze(resume, position("Java"));
        assertTrue(result.getMatchScore() >= 20);
    }

    private JobResume resume(String content) {
        JobResume resume = new JobResume();
        resume.setId(1L);
        resume.setTitle("后端简历");
        resume.setContent(content);
        resume.setSummary("熟悉 Java 后端开发");
        resume.setTargetPosition("Java 后端");
        return resume;
    }

    private JobPosition position(String requirements) {
        JobPosition position = new JobPosition();
        position.setId(2L);
        position.setPosition("Java 后端开发工程师");
        position.setCompany("某公司");
        position.setJobDesc("负责核心业务系统开发");
        position.setRequirements(requirements);
        return position;
    }
}
