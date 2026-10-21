package com.nuist.agent.mapper;

import com.nuist.agent.entity.InterviewQuestion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 面试题目与作答 Mapper（成员 C：逐题作答、单题评分）。
 * 表结构见 sql/job_portal.sql —— t_job_interview_question。
 */
public interface InterviewQuestionMapper {

    /** 批量插入题目（一次生成整套问题） */
    @Insert("""
            <script>
            insert into t_job_interview_question(session_id, question_no, question, question_type,
                                                 expected_points, status, degraded)
            values
            <foreach collection="list" item="q" separator=",">
                (#{q.sessionId}, #{q.questionNo}, #{q.question}, #{q.questionType},
                 #{q.expectedPoints}, 0, #{q.degraded})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<InterviewQuestion> questions);

    /** 单条插入（追问场景） */
    @Insert("""
            insert into t_job_interview_question(session_id, question_no, question, question_type,
                                                 expected_points, status, degraded)
            values (#{sessionId}, #{questionNo}, #{question}, #{questionType},
                    #{expectedPoints}, 0, #{degraded})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InterviewQuestion question);

    /** 查询会话全部题目 */
    @Select("""
            select id, session_id, question_no, question, question_type, expected_points,
                   answer, answer_feedback, score, status, degraded, create_time, answer_time
            from t_job_interview_question
            where session_id = #{sessionId}
            order by question_no asc
            """)
    List<InterviewQuestion> selectBySessionId(@Param("sessionId") Long sessionId);

    /** 查询指定题号的题目 */
    @Select("""
            select id, session_id, question_no, question, question_type, expected_points,
                   answer, answer_feedback, score, status, degraded, create_time, answer_time
            from t_job_interview_question
            where session_id = #{sessionId} and question_no = #{questionNo}
            """)
    InterviewQuestion selectByQuestionNo(@Param("sessionId") Long sessionId,
                                         @Param("questionNo") int questionNo);

    /** 写入作答与点评 */
    @Update("""
            update t_job_interview_question
            set answer = #{answer},
                answer_feedback = #{feedback},
                score = #{score},
                status = 1,
                degraded = #{degraded},
                answer_time = now()
            where session_id = #{sessionId} and question_no = #{questionNo}
            """)
    int updateAnswer(@Param("sessionId") Long sessionId,
                     @Param("questionNo") int questionNo,
                     @Param("answer") String answer,
                     @Param("feedback") String feedback,
                     @Param("score") Integer score,
                     @Param("degraded") int degraded);

    /** 当前最大题号（无题目时返回 null） */
    @Select("select max(question_no) from t_job_interview_question where session_id = #{sessionId}")
    Integer selectMaxQuestionNo(@Param("sessionId") Long sessionId);

    /** 已作答题数 */
    @Select("""
            select count(*) from t_job_interview_question
            where session_id = #{sessionId} and status = 1
            """)
    int countAnswered(@Param("sessionId") Long sessionId);
}
