package com.nuist.agent.mapper;

import com.nuist.agent.entity.UserSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 登录会话（令牌）Mapper（成员 A：用户与权限）。
 * 表结构见 sql/job_portal.sql —— t_job_user_session。
 */
public interface UserSessionMapper {

    /** 签发令牌 */
    @Insert("""
            insert into t_job_user_session(user_id, username, token, expire_time, last_access_time)
            values (#{userId}, #{username}, #{token}, #{expireTime}, now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserSession session);

    /** 按令牌查询会话 */
    @Select("""
            select id, user_id, username, token, expire_time, last_access_time, create_time
            from t_job_user_session
            where token = #{token}
            """)
    UserSession selectByToken(@Param("token") String token);

    /** 续期（滑动过期） */
    @Update("""
            update t_job_user_session
            set last_access_time = now(), expire_time = #{expireTime}
            where token = #{token}
            """)
    int refresh(@Param("token") String token, @Param("expireTime") LocalDateTime expireTime);

    /** 注销令牌 */
    @Delete("delete from t_job_user_session where token = #{token}")
    int deleteByToken(@Param("token") String token);

    /** 清理过期令牌 */
    @Delete("delete from t_job_user_session where expire_time < now()")
    int deleteExpired();

    /** 统计用户在线会话数（排查用） */
    @Select("select count(*) from t_job_user_session where user_id = #{userId} and expire_time > now()")
    int countActiveByUser(@Param("userId") Long userId);
}
