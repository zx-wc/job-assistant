package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟面试会话实体（成员 C：文字模拟面试）。
 * 对应表 t_job_interview_session：一次练习一条记录，status=0 进行中 / 1 已结束。
 */
@Data
public class InterviewSession {

    /** 主键 */
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 关联简历（可空） */
    private Long resumeId;

    /** 关联岗位（可空） */
    private Long positionId;

    /** 面试标题 */
    private String title;

    /** 难度 EASY / NORMAL / HARD */
    private String difficulty;

    /** 题目总数 */
    private Integer totalQuestions;

    /** 已作答题数 */
    private Integer answeredCount;

    /** 0 进行中 / 1 已结束 */
    private Integer status;

    /** 综合得分 */
    private Integer overallScore;

    /** 问题生成所用模型 */
    private String model;

    /** 1 表示题目为本地降级生成 */
    private Integer degraded;

    /** 开始时间 */
    private LocalDateTime createTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 以下字段仅用于列表展示（连表查询），不落库 */
    private String positionTitle;

    /** 简历标题（连表查询，不落库） */
    private String resumeTitle;

    /** 公司名称（连表查询，不落库） */
    private String company;
}
