package com.nuist.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

// 用户账号实体 用于登录注册
@Data
public class UserAccount {
    private Long id;          // 用户主键
    private String username;  // 登录用户名
    private String password;  // 密码(MD5加盐后存储)
    private String nickname;  // 昵称
    private LocalDateTime createTime; // 注册时间
}
