package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位信息实体（成员 B：岗位信息管理）。
 * 对应表 t_job_position：用户关注的招聘岗位，作为简历分析与模拟面试的输入。
 */
@Data
public class JobPosition {

    /** 主键 */
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 公司名称 */
    private String company;

    /** 岗位名称 */
    private String position;

    /** 工作城市 */
    private String city;

    /** 薪资范围 */
    private String salaryRange;

    /** 岗位职责描述 */
    private String jobDesc;

    /** 任职要求 */
    private String requirements;

    /** 来源链接 */
    private String sourceUrl;

    /** 1 关注中 / 0 已关闭 */
    private Integer status;

    /** 逻辑删除 0 正常 / 1 已删除 */
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
