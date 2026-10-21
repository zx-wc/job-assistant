package com.nuist.agent.job.interview;

import com.nuist.agent.entity.InterviewQuestion;
import com.nuist.agent.entity.InterviewSession;
import com.nuist.agent.zhangxu.ai.AiResults;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模拟面试的本地降级策略（成员 C：风险应对）。
 *
 * <p>大模型不可用时，仍然要保证"能练习、能打分、能出报告"：
 * 题目用岗位/简历关键词模板生成，单题分数用回答长度、要点覆盖、结构词、量化数据等启发式计算，
 * 报告则由各题得分聚合而成。所有降级结果都会标记 degraded=true。</p>
 */
@Component
public class FallbackInterviewCoach {

    /** 回答里出现这些结构词说明表达有逻辑（STAR/因果） */
    private static final List<String> STRUCTURE_WORDS = List.of(
            "首先", "其次", "然后", "最后", "因为", "所以", "结果", "背景", "任务", "行动",
            "第一", "第二", "第三", "总结", "另外", "同时");

    /**
     * 生成本地降级题目。
     *
     * @param session      会话（提供岗位/难度）
     * @param keywords     从岗位要求中提取的关键词（可为空）
     * @param questionCount 题目数量
     * @return 题目列表
     */
    public AiResults.QuestionListResult generateQuestions(InterviewSession session, List<String> keywords,
                                                          int questionCount) {
        String position = StringUtils.hasText(session.getTitle()) ? session.getTitle() : "目标岗位";
        List<AiResults.QuestionItem> items = new ArrayList<>();
        List<String> focus = new ArrayList<>();
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && !focus.contains(keyword)) {
                focus.add(keyword);
            }
        }
        if (focus.isEmpty()) {
            focus.addAll(List.of("项目经历", "技术基础", "问题定位"));
        }
        int index = 0;
        while (items.size() < questionCount) {
            String keyword = focus.get(index % focus.size());
            switch (index % 5) {
                case 0 -> items.add(item("在" + position + "的岗位要求中，" + keyword
                        + " 是重要能力。请结合你做过的事情说明你如何使用 " + keyword + "，达到了什么效果？",
                        "技能", List.of("是否说明使用场景", "是否有具体做法", "是否有可验证的结果")));
                case 1 -> items.add(item("请挑一个与你应聘方向最相关的项目，说明你的职责、技术选型理由以及最终结果（最好带上数据）。",
                        "项目", List.of("项目背景与目标", "个人职责边界", "技术选型理由", "量化结果")));
                case 2 -> items.add(item("如果线上接口的响应时间突然从 100ms 涨到 2s，你会按什么顺序排查？请说明你的判断依据。",
                        "技能", List.of("排查顺序是否清晰", "是否会看监控与日志", "是否能定位到索引/缓存/线程等常见原因")));
                case 3 -> items.add(item("请用 STAR 结构讲一次你在团队协作中推动事情落地的经历：当时的情景、你的任务、采取的行动和最终结果。",
                        "行为", List.of("情景与任务描述清楚", "行动体现主动性", "结果可衡量")));
                default -> items.add(item("你为什么选择" + position + "这个方向？未来 1-2 年的学习和成长计划是什么？",
                        "岗位匹配", List.of("动机是否真实", "对岗位理解是否到位", "计划是否具体可执行")));
            }
            index++;
        }
        AiResults.QuestionListResult result = new AiResults.QuestionListResult();
        result.setQuestions(items);
        return result;
    }

    /**
     * 本地降级单题评分。
     *
     * @param question       题目
     * @param answer         回答
     * @param expectedPoints 考察要点
     * @return 评分结果
     */
    public AiResults.AnswerEvalResult evaluate(String question, String answer, List<String> expectedPoints) {
        String text = answer == null ? "" : answer.trim();
        int length = text.length();
        int score = 25;
        if (length >= 40) {
            score += 15;
        }
        if (length >= 120) {
            score += 10;
        }
        if (length >= 260) {
            score += 5;
        }
        int covered = 0;
        List<String> missing = new ArrayList<>();
        for (String point : expectedPoints) {
            if (StringUtils.hasText(point) && containsPoint(text, point)) {
                covered++;
            } else if (StringUtils.hasText(point)) {
                missing.add(point);
            }
        }
        score += Math.min(25, covered * 8);
        int structureHits = 0;
        for (String word : STRUCTURE_WORDS) {
            if (text.contains(word)) {
                structureHits++;
            }
        }
        if (structureHits >= 2) {
            score += 10;
        }
        if (hasQuantified(text)) {
            score += 10;
        }
        score = Math.max(20, Math.min(score, 92));

        AiResults.AnswerEvalResult result = new AiResults.AnswerEvalResult();
        result.setScore(score);
        result.setComment(buildComment(score, length, covered, expectedPoints.size()));
        List<String> highlights = new ArrayList<>();
        if (structureHits >= 2) {
            highlights.add("回答有清晰的结构（使用了" + structureHits + "个逻辑连接词），便于面试官跟上思路。");
        }
        if (covered > 0) {
            highlights.add("覆盖了 " + covered + " 个考察要点。");
        }
        if (hasQuantified(text)) {
            highlights.add("给出了量化数据，结论更有说服力。");
        }
        if (highlights.isEmpty()) {
            highlights.add("回答已回应题目，具备继续深挖的基础。");
        }
        List<String> improvements = new ArrayList<>();
        if (length < 120) {
            improvements.add("回答偏短，建议补充背景、你的具体做法和结果，控制在 150-300 字。");
        }
        if (!missing.isEmpty()) {
            improvements.add("补充以下考察点：" + String.join("、", missing) + "。");
        }
        if (!hasQuantified(text)) {
            improvements.add("加上可验证的数据（耗时、QPS、命中率、覆盖率等），避免只说“提升了性能”。");
        }
        improvements.add("用 STAR 结构组织：情景 → 任务 → 行动 → 结果，先给结论再展开。");
        result.setHighlights(highlights);
        result.setImprovements(improvements);
        return result;
    }

    /**
     * 本地降级整场报告。
     *
     * @param session   会话
     * @param questions 题目（含已作答题目的评分）
     * @return 报告
     */
    public AiResults.InterviewReportResult report(InterviewSession session, List<InterviewQuestion> questions) {
        int total = 0;
        int answeredCount = 0;
        int questionCount = questions.size();
        List<String> strong = new ArrayList<>();
        List<String> weak = new ArrayList<>();
        int skillSum = 0;
        int skillCount = 0;
        int projectSum = 0;
        int projectCount = 0;
        for (InterviewQuestion question : questions) {
            if (question.getScore() == null) {
                // 未作答的题目不计入平均分，但会在总结中提示
                continue;
            }
            int score = Math.max(0, Math.min(100, question.getScore()));
            total += score;
            answeredCount++;
            if (score >= 75) {
                strong.add("第" + question.getQuestionNo() + "题（" + question.getQuestionType() + "）表现较好，得分 " + score + "。");
            } else if (score < 60) {
                weak.add("第" + question.getQuestionNo() + "题（" + question.getQuestionType()
                        + "）得分偏低（" + score + "），回答缺少细节或未覆盖考察要点。");
            }
            if ("技能".equals(question.getQuestionType())) {
                skillSum += score;
                skillCount++;
            } else if ("项目".equals(question.getQuestionType())) {
                projectSum += score;
                projectCount++;
            }
        }
        int overall = answeredCount == 0 ? 0 : (int) Math.round((double) total / answeredCount);

        AiResults.InterviewReportResult result = new AiResults.InterviewReportResult();
        result.setOverallScore(overall);
        result.setSummary("（本地规则评分）本场共 " + questionCount + " 道题，已作答 " + answeredCount
                + " 道，平均得分 " + overall
                + " 分（未作答题目不计入平均分）。评分依据为回答长度、考察要点覆盖、表达结构与量化数据，未调用大模型；"
                + "AI 服务恢复后可重新生成更细粒度的反馈报告。");
        if (strong.isEmpty()) {
            strong.add("完成了本场练习，" + (answeredCount > 0 ? "共作答 " + answeredCount + " 道题" : "建议至少完成 3 道题以获得有效反馈") + "。");
        }
        if (weak.isEmpty()) {
            weak.add("暂未发现明显失分点，可继续提高回答深度（原理、边界、取舍）。");
        }
        result.setStrengths(strong);
        result.setWeaknesses(weak);
        result.setSuggestions(new ArrayList<>(List.of(
                "针对得分低于 60 的题目重新作答一次，补齐考察要点与数据。",
                "每道题控制在 150-300 字：结论先行，再讲做法与结果。",
                "准备 2-3 个可深挖的项目细节，包括技术选型取舍与踩坑过程。",
                "把岗位 JD 中的关键词逐个对应到自己的经历，形成“能力-事例”对照表。"
        )));
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("专业知识", skillCount == 0 ? overall : (int) Math.round((double) skillSum / skillCount));
        dimensions.put("表达逻辑", Math.min(95, overall + 5));
        dimensions.put("项目深度", projectCount == 0 ? Math.max(30, overall - 5)
                : (int) Math.round((double) projectSum / projectCount));
        dimensions.put("岗位匹配", Math.max(30, Math.min(95, overall)));
        result.setDimensionScores(dimensions);
        return result;
    }

    private AiResults.QuestionItem item(String question, String type, List<String> points) {
        AiResults.QuestionItem item = new AiResults.QuestionItem();
        item.setQuestion(question);
        item.setType(type);
        item.setExpectedPoints(new ArrayList<>(points));
        return item;
    }

    private boolean containsPoint(String answer, String point) {
        String normalized = answer.toLowerCase(Locale.ROOT);
        // 要点通常是一句话，按其中较长的词片段做包含判断
        String[] fragments = point.split("[、，,；;\\s]+");
        for (String fragment : fragments) {
            if (fragment.length() >= 2 && normalized.contains(fragment.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return normalized.contains(point.toLowerCase(Locale.ROOT));
    }

    private boolean hasQuantified(String text) {
        return text.contains("%") || text.contains("ms") || text.contains("QPS")
                || text.contains("qps") || text.chars().anyMatch(Character::isDigit);
    }

    private String buildComment(int score, int length, int covered, int totalPoints) {
        StringBuilder comment = new StringBuilder("（本地规则评分）");
        if (score >= 80) {
            comment.append("回答较完整，结构与细节都比较好。");
        } else if (score >= 65) {
            comment.append("回答基本切题，但深度和细节还有提升空间。");
        } else if (score >= 45) {
            comment.append("回答偏简单，关键要点没有展开，缺少可验证的过程与结果。");
        } else {
            comment.append("回答过于简短或未回应题目，面试官难以判断你的真实水平。");
        }
        comment.append("当前长度 ").append(length).append(" 字");
        if (totalPoints > 0) {
            comment.append("，覆盖考察要点 ").append(covered).append("/").append(totalPoints).append(" 个");
        }
        comment.append("。");
        return comment.toString();
    }
}
