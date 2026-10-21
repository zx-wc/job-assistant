package com.nuist.agent.zhangxu.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户角色实体（成员 A：权限校验）。
 * 对应表 t_job_user_role：USER / ADMIN，管理员可管理提示词模板、查看 AI 调用统计。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    /** 用户主键 */
    private Long userId;

    /** 角色 USER / ADMIN */
    private String role;

    /** 创建时间 */
    private LocalDateTime createTime;
}
