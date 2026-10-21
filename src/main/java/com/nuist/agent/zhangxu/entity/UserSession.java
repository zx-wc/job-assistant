package com.nuist.agent.zhangxu.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录会话（令牌）实体（成员 A：用户与权限）。
 * 对应表 t_job_user_session：令牌为 32 位随机串，只存服务端库表，前端通过请求头携带。
 */
@Data
public class UserSession {

    /** 主键 */
    private Long id;

    /** 用户主键 */
    private Long userId;

    /** 用户名（冗余，便于排查） */
    private String username;

    /** 登录令牌 */
    private String token;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 最后访问时间 */
    private LocalDateTime lastAccessTime;

    /** 签发时间 */
    private LocalDateTime createTime;
}
