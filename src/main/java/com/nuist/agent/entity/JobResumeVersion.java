package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历版本快照实体（成员 B：简历版本保存与回溯）。
 * 对应表 t_job_resume_version：(resume_id, version_no) 唯一。
 */
@Data
public class JobResumeVersion {

    /** 主键 */
    private Long id;

    /** 简历主键 */
    private Long resumeId;

    /** 版本号，从 1 递增 */
    private Integer versionNo;

    /** 该版本标题快照 */
    private String title;

    /** 该版本正文快照 */
    private String content;

    /** 版本说明 */
    private String changeNote;

    /** 创建时间 */
    private LocalDateTime createTime;
}
