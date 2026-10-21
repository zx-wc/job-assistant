package com.nuist.agent.mapper;

import com.nuist.agent.entity.InterviewFeedback;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 面试反馈报告 Mapper（成员 C：面试反馈与记录）。
 * 表结构见 sql/job_portal.sql —— t_job_interview_feedback（session_id 唯一）。
 */
public interface InterviewFeedbackMapper {

    /** 新增反馈报告，主键回填 */
    @Insert("""
            insert into t_job_interview_feedback(session_id, overall_score, summary, strengths,
                                                 weaknesses, suggestions, dimension_scores,
                                                 model, degraded)
            values (#{sessionId}, #{overallScore}, #{summary}, #{strengths},
                    #{weaknesses}, #{suggestions}, #{dimensionScores},
                    #{model}, #{degraded})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InterviewFeedback feedback);

    /** 按会话查询报告 */
    @Select("""
            select id, session_id, overall_score, summary, strengths, weaknesses, suggestions,
                   dimension_scores, model, degraded, create_time
            from t_job_interview_feedback
            where session_id = #{sessionId}
            """)
    InterviewFeedback selectBySessionId(@Param("sessionId") Long sessionId);
}
