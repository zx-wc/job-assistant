package com.nuist.agent.job.auth;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.UserAccount;
import com.nuist.agent.entity.UserRole;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.config.JobProperties;
import com.nuist.agent.job.dto.AuthDtos;
import com.nuist.agent.mapper.UserRoleMapper;
import com.nuist.agent.service.IUserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 用户与权限服务实现（成员 A）。
 */
@Service
public class JobAuthServiceImpl implements JobAuthService {

    private static final Logger log = LoggerFactory.getLogger(JobAuthServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IUserAccountService userAccountService;
    private final UserRoleMapper userRoleMapper;
    private final TokenService tokenService;
    private final JobProperties jobProperties;

    public JobAuthServiceImpl(IUserAccountService userAccountService,
                              UserRoleMapper userRoleMapper,
                              TokenService tokenService,
                              JobProperties jobProperties) {
        this.userAccountService = userAccountService;
        this.userRoleMapper = userRoleMapper;
        this.tokenService = tokenService;
        this.jobProperties = jobProperties;
    }

    @Override
    public AuthDtos.AuthVO register(AuthDtos.RegisterRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        String username = trim(request.getUsername());
        // 用户名唯一性、长度、密码强度校验都在既有 UserAccountServiceImpl 中完成
        UserAccount account = userAccountService.register(username, request.getPassword(), request.getNickname());
        String role = resolveInitialRole(username);
        userRoleMapper.insertIfAbsent(account.getId(), role);
        log.info("求职系统用户注册成功:username={},id={},role={}", username, account.getId(), role);
        return buildAuthVO(account, role);
    }

    @Override
    public AuthDtos.AuthVO login(AuthDtos.LoginRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        String username = trim(request.getUsername());
        // 用户名/密码错误由既有服务抛出 USERNAME_OR_PASSWORD_ERROR
        UserAccount account = userAccountService.login(username, request.getPassword());
        String role = currentRole(account.getId(), username);
        log.info("求职系统用户登录成功:username={},id={},role={}", username, account.getId(), role);
        return buildAuthVO(account, role);
    }

    @Override
    public AuthDtos.UserVO currentUser() {
        Long userId = UserContextHolder.requireUserId();
        AuthDtos.UserVO vo = new AuthDtos.UserVO();
        vo.setId(userId);
        vo.setUsername(UserContextHolder.getUsername());
        vo.setRole(UserContextHolder.getRole());
        // 昵称不落令牌，这里从角色表无必要信息，直接用用户名兜底
        vo.setNickname(UserContextHolder.getUsername());
        return vo;
    }

    @Override
    public void logout(String token) {
        tokenService.revoke(token);
    }

    private AuthDtos.AuthVO buildAuthVO(UserAccount account, String role) {
        TokenService.IssuedToken issued = tokenService.issue(account.getId(), account.getUsername());
        AuthDtos.UserVO userVO = new AuthDtos.UserVO();
        userVO.setId(account.getId());
        userVO.setUsername(account.getUsername());
        userVO.setNickname(StringUtils.hasText(account.getNickname()) ? account.getNickname() : account.getUsername());
        userVO.setRole(role);
        AuthDtos.AuthVO authVO = new AuthDtos.AuthVO();
        authVO.setUser(userVO);
        authVO.setToken(issued.token());
        authVO.setExpireTime(issued.expireTime().format(TIME_FORMAT));
        return authVO;
    }

    private String resolveInitialRole(String username) {
        List<String> admins = jobProperties.getSecurity().getAdminUsernames();
        if (admins != null && admins.stream().anyMatch(name -> name.equalsIgnoreCase(username))) {
            return "ADMIN";
        }
        return "USER";
    }

    private String currentRole(Long userId, String username) {
        String expected = resolveInitialRole(username);
        UserRole role = userRoleMapper.selectByUserId(userId);
        if (role == null) {
            userRoleMapper.insertIfAbsent(userId, expected);
            return expected;
        }
        // 配置中的管理员用户名始终以 ADMIN 生效
        if ("ADMIN".equals(expected) && !"ADMIN".equalsIgnoreCase(role.getRole())) {
            userRoleMapper.upsert(userId, "ADMIN");
            return "ADMIN";
        }
        return role.getRole() == null ? "USER" : role.getRole().toUpperCase();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
