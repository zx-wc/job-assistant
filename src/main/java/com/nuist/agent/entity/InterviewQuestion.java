package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试题目与作答实体（成员 C：逐题作答、单题点评）。
 * 对应表 t_job_interview_question：(session_id, question_no) 唯一。
 */
@Data
public class InterviewQuestion {

    /** 主键 */
    private Long id;

    /** 所属会话 */
    private Long sessionId;

    /** 题号，从 1 递增 */
    private Integer questionNo;

    /** 题干 */
    private String question;

    /** 题型：技能 / 项目 / 行为 / 岗位匹配 / 追问 */
    private String questionType;

    /** 考察要点（JSON 数组字符串） */
    private String expectedPoints;

    /** 考生作答文本 */
    private String answer;

    /** 单题点评 */
    private String answerFeedback;

    /** 单题得分 0-100 */
    private Integer score;

    /** 0 待作答 / 1 已作答 */
    private Integer status;

    /** 1 表示评分为本地降级结果 */
    private Integer degraded;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 作答时间 */
    private LocalDateTime answerTime;
}
