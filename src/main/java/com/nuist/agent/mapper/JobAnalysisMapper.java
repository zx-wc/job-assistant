package com.nuist.agent.mapper;

import com.nuist.agent.entity.JobAnalysis;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 简历与岗位分析 Mapper（成员 B：分析报告结构化存储）。
 * 表结构见 sql/job_portal.sql —— t_job_analysis。
 * 结构化字段（优势/短板/建议/缺失技能/面试重点/维度评分）以 JSON 文本存储。
 */
public interface JobAnalysisMapper {

    String COLUMNS = "a.id, a.user_id, a.resume_id, a.position_id, a.match_score, a.summary, "
            + "a.strengths, a.weaknesses, a.suggestions, a.missing_skills, a.interview_focus, "
            + "a.dimension_scores, a.model, a.degraded, a.create_time";

    /** 新增分析报告，主键回填 */
    @Insert("""
            insert into t_job_analysis(user_id, resume_id, position_id, match_score, summary,
                                       strengths, weaknesses, suggestions, missing_skills,
                                       interview_focus, dimension_scores, model, degraded)
            values (#{userId}, #{resumeId}, #{positionId}, #{matchScore}, #{summary},
                    #{strengths}, #{weaknesses}, #{suggestions}, #{missingSkills},
                    #{interviewFocus}, #{dimensionScores}, #{model}, #{degraded})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(JobAnalysis analysis);

    /** 按主键 + 用户查询报告详情（连表带出简历标题、岗位名称与公司） */
    @Select("""
            select a.id, a.user_id, a.resume_id, a.position_id, a.match_score, a.summary,
                   a.strengths, a.weaknesses, a.suggestions, a.missing_skills, a.interview_focus,
                   a.dimension_scores, a.model, a.degraded, a.create_time,
                   r.title as resume_title, p.position as position_title, p.company as company
            from t_job_analysis a
            left join t_job_resume r on r.id = a.resume_id
            left join t_job_position p on p.id = a.position_id
            where a.id = #{id} and a.user_id = #{userId}
            """)
    JobAnalysis selectById(@Param("id") Long id, @Param("userId") Long userId);

    /** 分页查询报告列表（按时间倒序） */
    @Select("""
            select a.id, a.user_id, a.resume_id, a.position_id, a.match_score, a.summary,
                   a.strengths, a.weaknesses, a.suggestions, a.missing_skills, a.interview_focus,
                   a.dimension_scores, a.model, a.degraded, a.create_time,
                   r.title as resume_title, p.position as position_title, p.company as company
            from t_job_analysis a
            left join t_job_resume r on r.id = a.resume_id
            left join t_job_position p on p.id = a.position_id
            where a.user_id = #{userId}
            order by a.id desc
            limit #{offset}, #{size}
            """)
    List<JobAnalysis> selectByPage(@Param("userId") Long userId,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    /** 统计报告总数 */
    @Select("select count(*) from t_job_analysis where user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);

    /** 删除报告 */
    @Delete("delete from t_job_analysis where id = #{id} and user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    /** 概览页：最近一条报告的匹配度 */
    @Select("""
            select match_score from t_job_analysis
            where user_id = #{userId}
            order by id desc limit 1
            """)
    Integer selectLatestMatchScore(@Param("userId") Long userId);
}
