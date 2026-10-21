package com.nuist.agent.mapper;

import com.nuist.agent.entity.JobResume;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 简历资料 Mapper（成员 B：简历资料管理）。
 * 表结构见 sql/job_portal.sql —— t_job_resume。
 * 所有查询都带 user_id 条件，保证用户间数据隔离；删除为逻辑删除（deleted=1）。
 * 注意：列表查询不返回 content（正文较大），详情才返回。
 */
public interface JobResumeMapper {

    String COLUMNS = "id, user_id, title, candidate_name, target_position, summary, status, "
            + "current_version, deleted, create_time, update_time";

    /** 新增简历，主键回填 */
    @Insert("""
            insert into t_job_resume(user_id, title, candidate_name, target_position, summary,
                                     content, status, current_version, deleted)
            values (#{userId}, #{title}, #{candidateName}, #{targetPosition}, #{summary},
                    #{content}, #{status}, #{currentVersion}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(JobResume resume);

    /** 按主键 + 用户查询详情（含正文） */
    @Select("""
            select id, user_id, title, candidate_name, target_position, summary, content, status,
                   current_version, deleted, create_time, update_time
            from t_job_resume
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    JobResume selectById(@Param("id") Long id, @Param("userId") Long userId);

    /** 分页查询简历列表（不含正文） */
    @Select("""
            <script>
            select id, user_id, title, candidate_name, target_position, summary, status,
                   current_version, deleted, create_time, update_time
            from t_job_resume
            where user_id = #{userId} and deleted = 0
            <if test='keyword != null and keyword != ""'>
                and (title like concat('%', #{keyword}, '%')
                     or target_position like concat('%', #{keyword}, '%')
                     or candidate_name like concat('%', #{keyword}, '%'))
            </if>
            order by update_time desc, id desc
            limit #{offset}, #{size}
            </script>
            """)
    List<JobResume> selectByPage(@Param("userId") Long userId,
                                 @Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    /** 统计简历总数 */
    @Select("""
            <script>
            select count(*)
            from t_job_resume
            where user_id = #{userId} and deleted = 0
            <if test='keyword != null and keyword != ""'>
                and (title like concat('%', #{keyword}, '%')
                     or target_position like concat('%', #{keyword}, '%')
                     or candidate_name like concat('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    int countByPage(@Param("userId") Long userId, @Param("keyword") String keyword);

    /** 更新简历基础信息与正文 */
    @Update("""
            update t_job_resume
            set title = #{title},
                candidate_name = #{candidateName},
                target_position = #{targetPosition},
                summary = #{summary},
                content = #{content},
                status = #{status},
                update_time = now()
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    int update(JobResume resume);

    /** 更新当前版本号 */
    @Update("""
            update t_job_resume
            set current_version = #{versionNo}, update_time = now()
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    int updateCurrentVersion(@Param("id") Long id,
                             @Param("userId") Long userId,
                             @Param("versionNo") int versionNo);

    /** 逻辑删除 */
    @Update("""
            update t_job_resume
            set deleted = 1, update_time = now()
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    /** 统计用户简历数量（概览页使用） */
    @Select("select count(*) from t_job_resume where user_id = #{userId} and deleted = 0")
    int countByUser(@Param("userId") Long userId);

    /** 查询用户全部简历（含正文，供向量索引重建使用） */
    @Select("""
            select id, user_id, title, candidate_name, target_position, summary, content, status,
                   current_version, deleted, create_time, update_time
            from t_job_resume
            where user_id = #{userId} and deleted = 0
            order by id asc
            """)
    List<JobResume> selectAllByUser(@Param("userId") Long userId);
}
