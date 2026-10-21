package com.nuist.agent.zhangxu.auth;

/**
 * 当前登录用户（成员 A：用户与权限）。
 * 由 JobAuthInterceptor 解析令牌后放入 UserContextHolder，供业务层取 userId 做数据隔离。
 */
public class LoginUser {

    private final Long userId;
    private final String username;
    private final String role;

    public LoginUser(Long userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    @Override
    public String toString() {
        return "LoginUser{userId=" + userId + ", username='" + username + "', role='" + role + "'}";
    }
}
