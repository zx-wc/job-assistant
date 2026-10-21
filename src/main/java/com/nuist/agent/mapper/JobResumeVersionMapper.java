package com.nuist.agent.mapper;

import com.nuist.agent.entity.JobResumeVersion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 简历版本 Mapper（成员 B：版本保存与回溯）。
 * 表结构见 sql/job_portal.sql —— t_job_resume_version。
 */
public interface JobResumeVersionMapper {

    /** 新增版本快照，主键回填 */
    @Insert("""
            insert into t_job_resume_version(resume_id, version_no, title, content, change_note)
            values (#{resumeId}, #{versionNo}, #{title}, #{content}, #{changeNote})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(JobResumeVersion version);

    /** 查询某简历的全部版本（新版本在前，列表不返回正文） */
    @Select("""
            select id, resume_id, version_no, title, change_note, create_time
            from t_job_resume_version
            where resume_id = #{resumeId}
            order by version_no desc
            """)
    List<JobResumeVersion> selectByResumeId(@Param("resumeId") Long resumeId);

    /** 查询指定版本详情（含正文） */
    @Select("""
            select id, resume_id, version_no, title, content, change_note, create_time
            from t_job_resume_version
            where resume_id = #{resumeId} and version_no = #{versionNo}
            """)
    JobResumeVersion selectByVersionNo(@Param("resumeId") Long resumeId,
                                       @Param("versionNo") int versionNo);

    /** 查询当前最大版本号（无版本时返回 null） */
    @Select("select max(version_no) from t_job_resume_version where resume_id = #{resumeId}")
    Integer selectMaxVersionNo(@Param("resumeId") Long resumeId);

    /** 统计版本数量 */
    @Select("select count(*) from t_job_resume_version where resume_id = #{resumeId}")
    int countByResumeId(@Param("resumeId") Long resumeId);
}
