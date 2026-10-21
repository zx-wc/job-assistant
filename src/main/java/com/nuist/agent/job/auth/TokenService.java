package com.nuist.agent.job.auth;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.UserSession;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.config.JobProperties;
import com.nuist.agent.mapper.UserSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 登录令牌服务（成员 A：用户与权限）。
 *
 * <p>安全设计：
 * <ul>
 *   <li>令牌为 32 位 SecureRandom 十六进制串，只落库服务端，前端仅通过请求头携带；</li>
 *   <li>令牌有有效期（默认 7 天），临近过期自动滑动续期；</li>
 *   <li>注销即删除令牌记录，退出后立即失效；</li>
 *   <li>日志只打印令牌前 8 位，避免令牌泄露到日志文件。</li>
 * </ul>
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSessionMapper userSessionMapper;
    private final JobProperties jobProperties;

    public TokenService(UserSessionMapper userSessionMapper, JobProperties jobProperties) {
        this.userSessionMapper = userSessionMapper;
        this.jobProperties = jobProperties;
    }

    /**
     * 签发令牌。
     *
     * @param userId   用户主键
     * @param username 用户名
     * @return 令牌与过期时间
     */
    public IssuedToken issue(Long userId, String username) {
        String token = randomToken();
        LocalDateTime expireTime = LocalDateTime.now().plusHours(jobProperties.getSecurity().getTokenTtlHours());
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setUsername(username);
        session.setToken(token);
        session.setExpireTime(expireTime);
        userSessionMapper.insert(session);
        // 顺手清理过期令牌，避免表无限膨胀（失败不影响登录）
        try {
            userSessionMapper.deleteExpired();
        } catch (Exception e) {
            log.warn("清理过期令牌失败:{}", e.getMessage());
        }
        log.info("签发登录令牌:userId={},token={}***", userId, mask(token));
        return new IssuedToken(token, expireTime);
    }

    /**
     * 校验令牌并返回会话信息；令牌不存在或已过期返回 null。
     */
    public UserSession validate(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        UserSession session = userSessionMapper.selectByToken(token.trim());
        if (session == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.getExpireTime() == null || session.getExpireTime().isBefore(now)) {
            userSessionMapper.deleteByToken(token.trim());
            return null;
        }
        // 滑动续期：剩余有效期不足阈值时延长
        long remainingHours = java.time.Duration.between(now, session.getExpireTime()).toHours();
        if (remainingHours < jobProperties.getSecurity().getRefreshThresholdHours()) {
            LocalDateTime newExpire = now.plusHours(jobProperties.getSecurity().getTokenTtlHours());
            userSessionMapper.refresh(token.trim(), newExpire);
            session.setExpireTime(newExpire);
            session.setLastAccessTime(now);
        }
        return session;
    }

    /** 注销令牌 */
    public void revoke(String token) {
        if (StringUtils.hasText(token)) {
            userSessionMapper.deleteByToken(token.trim());
            log.info("注销登录令牌:token={}***", mask(token));
        }
    }

    /** 要求令牌有效，否则抛 401 */
    public UserSession requireSession(String token) {
        UserSession session = validate(token);
        if (session == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return session;
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String mask(String token) {
        return StringUtils.hasText(token) && token.length() > 8 ? token.substring(0, 8) : "****";
    }

    /** 签发结果 */
    public record IssuedToken(String token, LocalDateTime expireTime) {
    }
}
