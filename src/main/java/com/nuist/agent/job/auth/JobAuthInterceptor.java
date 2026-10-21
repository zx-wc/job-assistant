package com.nuist.agent.job.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.R;
import com.nuist.agent.entity.UserRole;
import com.nuist.agent.entity.UserSession;
import com.nuist.agent.mapper.UserRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录与权限拦截器（成员 A）。
 *
 * <p>拦截 /api/job/** 下所有需要登录的接口：
 * <ul>
 *   <li>从 Authorization: Bearer &lt;token&gt; / X-Auth-Token / token 参数解析令牌；</li>
 *   <li>令牌无效或过期 → 返回 {code:401}，前端统一跳转登录页；</li>
 *   <li>解析成功 → 把 LoginUser 放入 UserContextHolder，业务层据此做用户数据隔离；</li>
 *   <li>请求结束后清理 ThreadLocal，避免线程池复用导致串号。</li>
 * </ul>
 */
@Component
public class JobAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JobAuthInterceptor.class);
    private static final String AUTH_HEADER = "Authorization";
    private static final String TOKEN_HEADER = "X-Auth-Token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserRoleMapper userRoleMapper;
    private final ObjectMapper objectMapper;

    public JobAuthInterceptor(TokenService tokenService,
                              UserRoleMapper userRoleMapper,
                              ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.userRoleMapper = userRoleMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        UserSession session = tokenService.validate(token);
        if (session == null) {
            log.warn("未登录访问受保护接口:{} {}", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return false;
        }
        UserRole role = userRoleMapper.selectByUserId(session.getUserId());
        String roleName = role == null || !StringUtils.hasText(role.getRole()) ? "USER" : role.getRole();
        UserContextHolder.set(new LoginUser(session.getUserId(), session.getUsername(), roleName));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    /** 依次尝试 Authorization 头、X-Auth-Token 头、token 查询参数 */
    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(authorization)) {
            String value = authorization.trim();
            if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                return value.substring(BEARER_PREFIX.length()).trim();
            }
            return value;
        }
        String tokenHeader = request.getHeader(TOKEN_HEADER);
        if (StringUtils.hasText(tokenHeader)) {
            return tokenHeader.trim();
        }
        return request.getParameter("token");
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        R body = R.error(ErrorCode.UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }
}
