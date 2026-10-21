package com.nuist.agent.mapper;

import com.nuist.agent.entity.JobPosition;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 岗位信息 Mapper（成员 B：岗位信息管理）。
 * 表结构见 sql/job_portal.sql —— t_job_position。
 */
public interface JobPositionMapper {

    /** 新增岗位，主键回填 */
    @Insert("""
            insert into t_job_position(user_id, company, position, city, salary_range,
                                      job_desc, requirements, source_url, status, deleted)
            values (#{userId}, #{company}, #{position}, #{city}, #{salaryRange},
                    #{jobDesc}, #{requirements}, #{sourceUrl}, #{status}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(JobPosition position);

    /** 按主键 + 用户查询详情 */
    @Select("""
            select id, user_id, company, position, city, salary_range, job_desc, requirements,
                   source_url, status, deleted, create_time, update_time
            from t_job_position
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    JobPosition selectById(@Param("id") Long id, @Param("userId") Long userId);

    /** 分页查询岗位列表 */
    @Select("""
            <script>
            select id, user_id, company, position, city, salary_range, job_desc, requirements,
                   source_url, status, deleted, create_time, update_time
            from t_job_position
            where user_id = #{userId} and deleted = 0
            <if test='keyword != null and keyword != ""'>
                and (position like concat('%', #{keyword}, '%')
                     or company like concat('%', #{keyword}, '%')
                     or city like concat('%', #{keyword}, '%'))
            </if>
            order by update_time desc, id desc
            limit #{offset}, #{size}
            </script>
            """)
    List<JobPosition> selectByPage(@Param("userId") Long userId,
                                   @Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    /** 统计岗位总数 */
    @Select("""
            <script>
            select count(*)
            from t_job_position
            where user_id = #{userId} and deleted = 0
            <if test='keyword != null and keyword != ""'>
                and (position like concat('%', #{keyword}, '%')
                     or company like concat('%', #{keyword}, '%')
                     or city like concat('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    int countByPage(@Param("userId") Long userId, @Param("keyword") String keyword);

    /** 更新岗位信息 */
    @Update("""
            update t_job_position
            set company = #{company},
                position = #{position},
                city = #{city},
                salary_range = #{salaryRange},
                job_desc = #{jobDesc},
                requirements = #{requirements},
                source_url = #{sourceUrl},
                status = #{status},
                update_time = now()
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    int update(JobPosition position);

    /** 逻辑删除 */
    @Update("""
            update t_job_position
            set deleted = 1, update_time = now()
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    /** 统计用户岗位数量（概览页使用） */
    @Select("select count(*) from t_job_position where user_id = #{userId} and deleted = 0")
    int countByUser(@Param("userId") Long userId);

    /** 查询用户全部岗位（供向量索引重建使用） */
    @Select("""
            select id, user_id, company, position, city, salary_range, job_desc, requirements,
                   source_url, status, deleted, create_time, update_time
            from t_job_position
            where user_id = #{userId} and deleted = 0
            order by id asc
            """)
    List<JobPosition> selectAllByUser(@Param("userId") Long userId);
}
