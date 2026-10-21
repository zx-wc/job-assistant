package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历与岗位分析报告实体（成员 B：简历与岗位分析）。
 * 对应表 t_job_analysis：结构化建议以 JSON 文本落库，读取时由服务层反序列化为列表/评分对象。
 */
@Data
public class JobAnalysis {

    /** 主键 */
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 简历主键 */
    private Long resumeId;

    /** 岗位主键 */
    private Long positionId;

    /** 综合匹配度 0-100 */
    private Integer matchScore;

    /** 总体结论 */
    private String summary;

    /** 优势（JSON 数组字符串） */
    private String strengths;

    /** 短板（JSON 数组字符串） */
    private String weaknesses;

    /** 改进建议（JSON 数组字符串） */
    private String suggestions;

    /** 缺失技能 / 能力准备建议（JSON 数组字符串） */
    private String missingSkills;

    /** 面试准备重点（JSON 数组字符串） */
    private String interviewFocus;

    /** 维度评分（JSON 对象字符串） */
    private String dimensionScores;

    /** 生成该报告的模型 */
    private String model;

    /** 1 表示 AI 不可用时的本地降级结果 */
    private Integer degraded;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 以下字段仅用于列表展示（连表查询），不落库 */
    private String resumeTitle;

    /** 岗位名称（连表查询，不落库） */
    private String positionTitle;

    /** 公司名称（连表查询，不落库） */
    private String company;
}
