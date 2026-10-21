package com.nuist.agent.job.auth;

import com.nuist.agent.job.dto.AuthDtos;

/**
 * 用户与权限服务（成员 A）。
 * 复用既有 {@code IUserAccountService} 的注册/登录与 MD5 加盐校验，在其之上叠加：
 * 角色授予（USER / ADMIN）、令牌签发与注销。
 */
public interface JobAuthService {

    /** 注册并直接签发令牌 */
    AuthDtos.AuthVO register(AuthDtos.RegisterRequest request);

    /** 登录并签发令牌 */
    AuthDtos.AuthVO login(AuthDtos.LoginRequest request);

    /** 当前登录用户信息 */
    AuthDtos.UserVO currentUser();

    /** 注销当前令牌 */
    void logout(String token);
}
