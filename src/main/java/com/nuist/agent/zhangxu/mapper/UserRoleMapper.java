package com.nuist.agent.zhangxu.mapper;

import com.nuist.agent.zhangxu.entity.UserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户角色 Mapper（成员 A：权限校验）。
 * 表结构见 sql/job_portal.sql —— t_job_user_role。
 */
public interface UserRoleMapper {

    /** 按用户查询角色 */
    @Select("select user_id, role, create_time from t_job_user_role where user_id = #{userId}")
    UserRole selectByUserId(@Param("userId") Long userId);

    /** 写入角色（已存在则保持原角色） */
    @Insert("""
            insert into t_job_user_role(user_id, role)
            values (#{userId}, #{role})
            on duplicate key update user_id = user_id
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("role") String role);

    /** 覆盖角色 */
    @Insert("""
            insert into t_job_user_role(user_id, role)
            values (#{userId}, #{role})
            on duplicate key update role = values(role)
            """)
    int upsert(@Param("userId") Long userId, @Param("role") String role);
}
