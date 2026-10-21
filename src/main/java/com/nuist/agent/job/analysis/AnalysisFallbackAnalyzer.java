package com.nuist.agent.job.analysis;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.zhangxu.ai.AiResults;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 简历分析的本地降级策略（成员 B：风险应对）。
 *
 * <p>当大模型不可用、额度受限或返回内容无法解析时，用确定性的规则给出"能用"的分析结果：
 * 通过岗位关键词与简历文本的覆盖度计算匹配度，并输出可直接执行的建议。
 * 结果标记 degraded=true，前端会提示"AI 暂不可用，以下为本地规则分析"。</p>
 */
@Component
public class AnalysisFallbackAnalyzer {

    /** 常见岗位能力关键词（可按需扩充；英文关键词统一按小写匹配） */
    private static final List<String> SKILL_VOCABULARY = List.of(
            "Java", "Spring Boot", "Spring MVC", "Spring Cloud", "MyBatis", "MySQL", "Redis",
            "Linux", "Docker", "Kubernetes", "Git", "Maven", "Kafka", "RocketMQ", "RabbitMQ",
            "消息队列", "微服务", "分布式", "高并发", "缓存", "JVM", "多线程", "并发",
            "索引", "SQL优化", "单元测试", "算法", "数据结构", "计算机网络", "操作系统",
            "Python", "C++", "Go", "Vue", "React", "JavaScript", "Nginx", "Elasticsearch",
            "Dubbo", "Zookeeper", "CI/CD", "Jenkins", "性能优化", "团队协作", "沟通能力",
            "项目经验", "实习经历", "英语", "CET-6"
    );

    /**
     * 生成降级分析结果。
     *
     * @param resume   简历
     * @param position 岗位
     * @return 与 AI 输出同构的分析结果
     */
    public AiResults.ResumeAnalysisResult analyze(JobResume resume, JobPosition position) {
        String resumeText = lower(join(resume.getContent(), resume.getSummary(), resume.getTargetPosition(),
                resume.getTitle(), resume.getCandidateName()));
        String jobText = lower(join(position.getJobDesc(), position.getRequirements(),
                position.getPosition(), position.getCity()));

        Set<String> required = new LinkedHashSet<>();
        for (String skill : SKILL_VOCABULARY) {
            if (jobText.contains(skill.toLowerCase(Locale.ROOT))) {
                required.add(skill);
            }
        }
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : required) {
            if (resumeText.contains(skill.toLowerCase(Locale.ROOT))) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        boolean quantified = containsQuantifiedResult(resumeText);
        boolean hasProject = resumeText.contains("项目");
        boolean hasInternship = resumeText.contains("实习");

        int score = computeScore(required.size(), matched.size(), quantified, hasProject, hasInternship);

        AiResults.ResumeAnalysisResult result = new AiResults.ResumeAnalysisResult();
        result.setMatchScore(score);
        result.setSummary(buildSummary(required.size(), matched.size(), score, missing));
        result.setStrengths(buildStrengths(matched, quantified, hasProject, hasInternship));
        result.setWeaknesses(buildWeaknesses(missing, quantified, resume.getContent()));
        result.setSuggestions(buildSuggestions(missing, quantified));
        result.setMissingSkills(new ArrayList<>(missing));
        result.setInterviewFocus(buildInterviewFocus(missing));
        result.setDimensionScores(buildDimensionScores(score, quantified, hasProject,
                StringUtils.hasText(resume.getContent()) ? resume.getContent().length() : 0));
        return result;
    }

    private int computeScore(int requiredCount, int matchedCount, boolean quantified,
                             boolean hasProject, boolean hasInternship) {
        int score;
        if (requiredCount == 0) {
            // 岗位描述过于简单，无法提取关键词，给中性分
            score = 60;
        } else {
            score = 40 + (int) Math.round(50.0 * matchedCount / requiredCount);
        }
        if (quantified) {
            score += 5;
        }
        if (hasProject) {
            score += 3;
        }
        if (hasInternship) {
            score += 3;
        }
        return Math.max(20, Math.min(score, 95));
    }

    private String buildSummary(int requiredCount, int matchedCount, int score, List<String> missing) {
        if (requiredCount == 0) {
            return "（本地规则分析）岗位描述中没有识别到明确的能力关键词，综合匹配度给中性评估 " + score
                    + " 分。建议补充岗位职责与任职要求后重新分析，或检查 AI 服务配置获得更准确的结论。";
        }
        String missingText = missing.isEmpty() ? "简历已覆盖岗位要求的主要能力关键词。"
                : "简历未体现：" + String.join("、", missing.subList(0, Math.min(3, missing.size())))
                  + (missing.size() > 3 ? " 等" : "") + "。";
        return "（本地规则分析）岗位共识别出 " + requiredCount + " 项能力关键词，简历覆盖其中 "
                + matchedCount + " 项，综合匹配度 " + score + " 分。" + missingText
                + "该结论基于关键词覆盖与内容完整度计算，AI 服务恢复后可重新生成更细粒度的建议。";
    }

    private List<String> buildStrengths(List<String> matched, boolean quantified,
                                        boolean hasProject, boolean hasInternship) {
        List<String> strengths = new ArrayList<>();
        if (!matched.isEmpty()) {
            strengths.add("简历已体现岗位要求的 " + String.join("、", matched.subList(0, Math.min(4, matched.size())))
                    + " 等能力，具备基本的技能匹配度。");
        }
        if (hasProject) {
            strengths.add("简历包含项目经历，便于面试官针对项目细节展开追问。");
        }
        if (hasInternship) {
            strengths.add("有实习经历，说明已接触真实工程环境，能降低用人方的培养成本。");
        }
        if (quantified) {
            strengths.add("项目描述中包含量化结果（如耗时、命中率、百分比），表达更有说服力。");
        }
        if (strengths.isEmpty()) {
            strengths.add("简历结构完整，具备继续优化的基础。");
        }
        return strengths;
    }

    private List<String> buildWeaknesses(List<String> missing, boolean quantified, String content) {
        List<String> weaknesses = new ArrayList<>();
        if (!missing.isEmpty()) {
            weaknesses.add("岗位要求中的 " + String.join("、", missing.subList(0, Math.min(4, missing.size())))
                    + " 在简历中没有对应描述，容易被筛掉。");
        }
        if (!quantified) {
            weaknesses.add("项目成果缺少量化数据（QPS、耗时、命中率、覆盖率等），说服力不足。");
        }
        if (content == null || content.length() < 300) {
            weaknesses.add("简历正文偏短，项目背景、个人职责与产出描述不充分。");
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("暂未发现明显短板，建议进一步针对岗位关键词做定向优化。");
        }
        return weaknesses;
    }

    private List<String> buildSuggestions(List<String> missing, boolean quantified) {
        List<String> suggestions = new ArrayList<>();
        if (!missing.isEmpty()) {
            suggestions.add("补充 " + String.join("、", missing.subList(0, Math.min(3, missing.size())))
                    + " 的使用场景：写明在哪个项目、解决什么问题、达到什么效果。");
        }
        if (!quantified) {
            suggestions.add("把项目结果量化，例如“接口 P99 从 320ms 降到 90ms”“FAQ 命中率 72%”。");
        }
        suggestions.add("按岗位 JD 的关键词重排技能与项目顺序，让最相关的内容出现在简历前 1/3。");
        suggestions.add("每条项目经历采用“背景-任务-动作-结果”四段式，控制在 3 行以内。");
        suggestions.add("针对该岗位准备 2-3 个可深挖的技术点，避免被追问时答不出细节。");
        return suggestions;
    }

    private List<String> buildInterviewFocus(List<String> missing) {
        List<String> focus = new ArrayList<>();
        if (!missing.isEmpty()) {
            focus.add("岗位要求但简历未体现的 " + missing.get(0) + "：面试官很可能追问你是否了解、如何补齐。");
        }
        focus.add("简历中最复杂的项目：技术选型理由、你负责的部分、遇到的最大问题与解决过程。");
        focus.add("项目中的量化结果：数据怎么来的、如何压测或统计、有没有做对比实验。");
        focus.add("与岗位职责直接相关的场景题：如高并发下的缓存一致性、慢 SQL 优化思路。");
        return focus;
    }

    private Map<String, Integer> buildDimensionScores(int score, boolean quantified,
                                                      boolean hasProject, int contentLength) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("技能匹配", score);
        dimensions.put("项目经验", Math.min(95, (hasProject ? 70 : 45) + (quantified ? 12 : 0)));
        dimensions.put("岗位契合", Math.max(20, Math.min(95, score - 5)));
        dimensions.put("表达呈现", contentLength >= 600 ? 75 : contentLength >= 300 ? 65 : 50);
        return dimensions;
    }

    /** 是否包含量化结果（百分比、毫秒、QPS、倍数等） */
    private boolean containsQuantifiedResult(String text) {
        return text.contains("%") || text.contains("ms") || text.contains("qps")
                || text.contains("倍") || text.contains("提升") || text.contains("降低");
    }

    private String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                builder.append(part).append('\n');
            }
        }
        return builder.toString();
    }
}
