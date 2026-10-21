package com.nuist.agent.job.auth;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.exception.BusinessException;

/**
 * 当前请求的登录上下文（成员 A：用户与权限）。
 *
 * <p>实现方式：ThreadLocal。由拦截器在 preHandle 写入、afterCompletion 清理，
 * 业务层（Service）通过 requireUserId() 获取用户 id，杜绝前端伪造 userId 越权访问。</p>
 */
public final class UserContextHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    public static String getRole() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getRole();
    }

    /** 取当前用户 id，未登录直接抛 401 业务异常 */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 校验管理员权限，非管理员抛 403 */
    public static void requireAdmin() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!user.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
