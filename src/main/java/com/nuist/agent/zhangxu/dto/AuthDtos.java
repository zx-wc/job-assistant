package com.nuist.agent.zhangxu.dto;

import lombok.Data;

/**
 * 认证相关请求/响应 DTO（成员 A：用户与权限）。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 注册请求 */
    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private String nickname;
    }

    /** 登录请求 */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    /** 用户信息（不含密码） */
    @Data
    public static class UserVO {
        private Long id;
        private String username;
        private String nickname;
        private String role;
    }

    /** 登录/注册成功响应：用户信息 + 令牌 */
    @Data
    public static class AuthVO {
        private UserVO user;
        private String token;
        private String expireTime;
    }
}
