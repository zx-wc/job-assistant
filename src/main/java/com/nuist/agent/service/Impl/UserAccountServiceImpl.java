package com.nuist.agent.service.Impl;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.UserAccount;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.mapper.UserAccountMapper;
import com.nuist.agent.service.IUserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserAccountServiceImpl implements IUserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountServiceImpl.class);
    // 密码加盐 与用户密码拼接后再MD5 避免明文和彩虹表破解
    private static final String PASSWORD_SALT = "airui-mall";
    private final UserAccountMapper userAccountMapper;

    // 构造注入
    public UserAccountServiceImpl(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public UserAccount register(String username, String password, String nickname) {
        // 参数校验
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "用户名长度需在3-20位之间");
        }
        if (!StringUtils.hasText(password) || password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "密码长度不能少于6位");
        }
        // 用户名唯一性校验
        if (userAccountMapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 组装账号 密码加盐后MD5存储
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(encrypt(password));
        account.setNickname(StringUtils.hasText(nickname) ? nickname : username);
        userAccountMapper.insert(account);
        // 不向外暴露密码
        account.setPassword(null);
        log.info("用户注册成功:username={},id={}", account.getUsername(), account.getId());
        return account;
    }

    @Override
    public UserAccount login(String username, String password) {
        // 参数校验
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "用户名和密码不能为空");
        }
        // 查询用户
        UserAccount account = userAccountMapper.selectByUsername(username);
        if (account == null) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        // 校验密码
        if (!encrypt(password).equals(account.getPassword())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        // 不向外暴露密码
        account.setPassword(null);
        log.info("用户登录成功:username={},id={}", account.getUsername(), account.getId());
        return account;
    }

    // 密码加密 盐+明文拼接后取MD5
    private String encrypt(String rawPassword) {
        return DigestUtils.md5DigestAsHex((PASSWORD_SALT + rawPassword).getBytes(StandardCharsets.UTF_8));
    }
}
