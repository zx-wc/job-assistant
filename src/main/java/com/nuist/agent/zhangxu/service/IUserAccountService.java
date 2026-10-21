package com.nuist.agent.zhangxu.service;

import com.nuist.agent.zhangxu.entity.UserAccount;

// 用户账号服务接口 提供注册与登录能力
public interface IUserAccountService {

    // 注册新用户 返回注册成功的账号信息(不含密码)
    UserAccount register(String username, String password, String nickname);

    // 登录 校验用户名密码 返回账号信息(不含密码)
    UserAccount login(String username, String password);
}
