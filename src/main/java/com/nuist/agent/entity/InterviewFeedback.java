package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试反馈报告实体（成员 C：面试反馈与记录）。
 * 对应表 t_job_interview_feedback：一个会话一份报告（session_id 唯一）。
 */
@Data
public class InterviewFeedback {

    /** 主键 */
    private Long id;

    /** 所属会话 */
    private Long sessionId;

    /** 综合得分 0-100 */
    private Integer overallScore;

    /** 总体评价 */
    private String summary;

    /** 亮点（JSON 数组字符串） */
    private String strengths;

    /** 不足（JSON 数组字符串） */
    private String weaknesses;

    /** 改进建议（JSON 数组字符串） */
    private String suggestions;

    /** 维度评分（JSON 对象字符串） */
    private String dimensionScores;

    /** 生成报告的模型 */
    private String model;

    /** 1 表示本地降级结果 */
    private Integer degraded;

    /** 创建时间 */
    private LocalDateTime createTime;
}
