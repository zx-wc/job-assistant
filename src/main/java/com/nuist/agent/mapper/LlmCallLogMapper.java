package com.nuist.agent.mapper;

import com.nuist.agent.entity.LlmCallLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 大模型调用日志 Mapper（成员 A：AI 网关可观测性）。
 * 表结构见 sql/job_portal.sql —— t_llm_call_log。
 */
public interface LlmCallLogMapper {

    /** 写入调用日志 */
    @Insert("""
            insert into t_llm_call_log(user_id, scene, model, prompt_tokens, completion_tokens,
                                       total_tokens, duration_ms, retry_count, cached, success,
                                       degraded, error_msg)
            values (#{userId}, #{scene}, #{model}, #{promptTokens}, #{completionTokens},
                    #{totalTokens}, #{durationMs}, #{retryCount}, #{cached}, #{success},
                    #{degraded}, #{errorMsg})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LlmCallLog log);

    /** 分页查询调用日志 */
    @Select("""
            select id, user_id, scene, model, prompt_tokens, completion_tokens, total_tokens,
                   duration_ms, retry_count, cached, success, degraded, error_msg, create_time
            from t_llm_call_log
            order by id desc
            limit #{offset}, #{size}
            """)
    List<LlmCallLog> selectByPage(@Param("offset") int offset, @Param("size") int size);

    /** 统计日志总数 */
    @Select("select count(*) from t_llm_call_log")
    int count();

    /** 统计指定时间之后的调用总数 */
    @Select("select count(*) from t_llm_call_log where create_time >= #{from}")
    int countSince(@Param("from") LocalDateTime from);

    /** 统计指定时间之后的成功调用数 */
    @Select("select count(*) from t_llm_call_log where create_time >= #{from} and success = 1")
    int countSuccessSince(@Param("from") LocalDateTime from);

    /** 统计指定时间之后的降级次数 */
    @Select("select count(*) from t_llm_call_log where create_time >= #{from} and degraded = 1")
    int countDegradedSince(@Param("from") LocalDateTime from);

    /** 统计指定时间之后的缓存命中次数 */
    @Select("select count(*) from t_llm_call_log where create_time >= #{from} and cached = 1")
    int countCachedSince(@Param("from") LocalDateTime from);

    /** 平均耗时（毫秒） */
    @Select("select coalesce(round(avg(duration_ms)), 0) from t_llm_call_log where create_time >= #{from}")
    long avgDurationSince(@Param("from") LocalDateTime from);

    /** token 总量 */
    @Select("select coalesce(sum(total_tokens), 0) from t_llm_call_log where create_time >= #{from}")
    long sumTokensSince(@Param("from") LocalDateTime from);

    /** 按场景统计调用分布 */
    @Select("""
            select scene, count(*) as total, sum(success = 1) as success_count,
                   round(avg(duration_ms)) as avg_duration_ms
            from t_llm_call_log
            where create_time >= #{from}
            group by scene
            order by total desc
            """)
    List<Map<String, Object>> countBySceneSince(@Param("from") LocalDateTime from);

    /** 按天统计最近 N 天调用量 */
    @Select("""
            select date(create_time) as day, count(*) as total,
                   sum(success = 1) as success_count, sum(degraded = 1) as degraded_count
            from t_llm_call_log
            group by date(create_time)
            order by day desc
            limit #{days}
            """)
    List<Map<String, Object>> selectDailyStats(@Param("days") int days);
}
