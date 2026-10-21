package com.nuist.agent.zhangxu.controller;

import com.nuist.agent.zhangxu.common.ErrorCode;
import com.nuist.agent.zhangxu.entity.R;
import com.nuist.agent.zhangxu.auth.JobAuthInterceptor;
import com.nuist.agent.zhangxu.auth.JobAuthService;
import com.nuist.agent.zhangxu.dto.AuthDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户与权限接口（成员 A）。
 * 登录/注册返回令牌，后续请求通过 Authorization: Bearer &lt;token&gt; 携带。
 */
@RestController
@RequestMapping("/api/job/auth")
public class JobAuthController {

    private final JobAuthService jobAuthService;

    public JobAuthController(JobAuthService jobAuthService) {
        this.jobAuthService = jobAuthService;
    }

    /** 注册：{username, password, nickname} → {user, token, expireTime} */
    @PostMapping("/register")
    public R register(@RequestBody(required = false) AuthDtos.RegisterRequest request) {
        if (request == null) {
            return R.error(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        AuthDtos.AuthVO auth = jobAuthService.register(request);
        return R.ok().data("user", auth.getUser())
                .data("token", auth.getToken())
                .data("expireTime", auth.getExpireTime());
    }

    /** 登录：{username, password} → {user, token, expireTime} */
    @PostMapping("/login")
    public R login(@RequestBody(required = false) AuthDtos.LoginRequest request) {
        if (request == null) {
            return R.error(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        AuthDtos.AuthVO auth = jobAuthService.login(request);
        return R.ok().data("user", auth.getUser())
                .data("token", auth.getToken())
                .data("expireTime", auth.getExpireTime());
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public R me() {
        return R.ok().data("user", jobAuthService.currentUser());
    }

    /** 退出登录（注销令牌） */
    @PostMapping("/logout")
    public R logout(HttpServletRequest request) {
        jobAuthService.logout(JobAuthInterceptor.resolveToken(request));
        return R.ok();
    }

    /** 健康检查（无需登录，用于部署验证） */
    @GetMapping("/ping")
    public R ping() {
        return R.ok().data("service", "智能求职辅助系统").data("status", "UP");
    }
}
