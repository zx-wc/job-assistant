package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历资料实体（成员 B：简历资料管理）。
 * 对应表 t_job_resume：一份简历一条记录，content 为纯文本正文。
 * 合规说明：content 入库前由 PrivacyMasker 做手机号/邮箱/身份证脱敏。
 */
@Data
public class JobResume {

    /** 主键 */
    private Long id;

    /** 所属用户（t_user_account.id），用于数据隔离 */
    private Long userId;

    /** 简历标题 */
    private String title;

    /** 求职者姓名 */
    private String candidateName;

    /** 目标岗位 */
    private String targetPosition;

    /** 一句话自我概述 */
    private String summary;

    /** 简历正文（纯文本，已脱敏） */
    private String content;

    /** 1 正常 / 0 归档 */
    private Integer status;

    /** 当前版本号 */
    private Integer currentVersion;

    /** 逻辑删除 0 正常 / 1 已删除 */
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
