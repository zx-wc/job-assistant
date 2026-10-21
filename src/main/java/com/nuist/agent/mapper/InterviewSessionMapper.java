package com.nuist.agent.mapper;

import com.nuist.agent.entity.InterviewSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 模拟面试会话 Mapper（成员 C：文字模拟面试）。
 * 表结构见 sql/job_portal.sql —— t_job_interview_session。
 */
public interface InterviewSessionMapper {

    /** 新增会话，主键回填 */
    @Insert("""
            insert into t_job_interview_session(user_id, resume_id, position_id, title, difficulty,
                                                total_questions, answered_count, status, model, degraded)
            values (#{userId}, #{resumeId}, #{positionId}, #{title}, #{difficulty},
                    #{totalQuestions}, 0, 0, #{model}, #{degraded})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InterviewSession session);

    /** 按主键 + 用户查询会话（连表带出岗位/简历标题与公司） */
    @Select("""
            select s.id, s.user_id, s.resume_id, s.position_id, s.title, s.difficulty,
                   s.total_questions, s.answered_count, s.status, s.overall_score, s.model,
                   s.degraded, s.create_time, s.end_time,
                   p.position as position_title, p.company as company, r.title as resume_title
            from t_job_interview_session s
            left join t_job_position p on p.id = s.position_id
            left join t_job_resume r on r.id = s.resume_id
            where s.id = #{id} and s.user_id = #{userId}
            """)
    InterviewSession selectById(@Param("id") Long id, @Param("userId") Long userId);

    /** 分页查询会话列表（status 为空时不过滤） */
    @Select("""
            <script>
            select s.id, s.user_id, s.resume_id, s.position_id, s.title, s.difficulty,
                   s.total_questions, s.answered_count, s.status, s.overall_score, s.model,
                   s.degraded, s.create_time, s.end_time,
                   p.position as position_title, p.company as company, r.title as resume_title
            from t_job_interview_session s
            left join t_job_position p on p.id = s.position_id
            left join t_job_resume r on r.id = s.resume_id
            where s.user_id = #{userId}
            <if test='status != null'>
                and s.status = #{status}
            </if>
            order by s.id desc
            limit #{offset}, #{size}
            </script>
            """)
    List<InterviewSession> selectByPage(@Param("userId") Long userId,
                                        @Param("status") Integer status,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    /** 统计会话数量（status 为空时不过滤） */
    @Select("""
            <script>
            select count(*)
            from t_job_interview_session
            where user_id = #{userId}
            <if test='status != null'>
                and status = #{status}
            </if>
            </script>
            """)
    int countByUser(@Param("userId") Long userId, @Param("status") Integer status);

    /** 作答题数 +1 */
    @Update("""
            update t_job_interview_session
            set answered_count = answered_count + 1
            where id = #{id} and user_id = #{userId}
            """)
    int incrementAnswered(@Param("id") Long id, @Param("userId") Long userId);

    /** 结束会话并写入综合得分 */
    @Update("""
            update t_job_interview_session
            set status = 1, overall_score = #{overallScore}, end_time = now()
            where id = #{id} and user_id = #{userId}
            """)
    int finish(@Param("id") Long id,
               @Param("userId") Long userId,
               @Param("overallScore") Integer overallScore);

    /** 更新题目总数（追加追问后同步） */
    @Update("""
            update t_job_interview_session
            set total_questions = #{totalQuestions}
            where id = #{id} and user_id = #{userId}
            """)
    int updateTotalQuestions(@Param("id") Long id,
                             @Param("userId") Long userId,
                             @Param("totalQuestions") int totalQuestions);

    /** 概览页：统计进行中的会话数 */
    @Select("""
            select count(*) from t_job_interview_session
            where user_id = #{userId} and status = 0
            """)
    int countOngoing(@Param("userId") Long userId);
}
