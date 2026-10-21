package com.nuist.agent.job.auth;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.UserSession;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.config.JobProperties;
import com.nuist.agent.mapper.UserSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录令牌服务测试（成员 E：权限模块）。
 */
class TokenServiceTest {

    private UserSessionMapper sessionMapper;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(UserSessionMapper.class);
        tokenService = new TokenService(sessionMapper, new JobProperties());
    }

    @Test
    void issue_shouldStore32CharTokenWithExpiry() {
        TokenService.IssuedToken issued = tokenService.issue(7L, "jobuser");

        assertNotNull(issued.token());
        assertEquals(32, issued.token().length());
        assertTrue(issued.token().matches("[0-9a-f]{32}"));
        assertTrue(issued.expireTime().isAfter(LocalDateTime.now().plusHours(160)));

        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionMapper).insert(captor.capture());
        UserSession session = captor.getValue();
        assertEquals(7L, session.getUserId());
        assertEquals("jobuser", session.getUsername());
        assertEquals(issued.token(), session.getToken());
    }

    @Test
    void issue_shouldGenerateDistinctTokens() {
        assertFalse(tokenService.issue(1L, "a").token().equals(tokenService.issue(1L, "a").token()));
    }

    @Test
    void validate_validToken_shouldReturnSession() {
        UserSession session = new UserSession();
        session.setUserId(1L);
        session.setToken("t");
        session.setExpireTime(LocalDateTime.now().plusDays(6));
        when(sessionMapper.selectByToken("t")).thenReturn(session);

        UserSession result = tokenService.validate("t");
        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        // 剩余 6 天 > 续期阈值 24 小时，不应触发续期
        verify(sessionMapper, never()).refresh(anyString(), any());
    }

    @Test
    void validate_nearExpiry_shouldRefresh() {
        UserSession session = new UserSession();
        session.setUserId(1L);
        session.setToken("t");
        session.setExpireTime(LocalDateTime.now().plusHours(2));
        when(sessionMapper.selectByToken("t")).thenReturn(session);

        tokenService.validate("t");

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sessionMapper).refresh(eq("t"), captor.capture());
        assertTrue(captor.getValue().isAfter(LocalDateTime.now().plusHours(100)));
    }

    @Test
    void validate_expiredToken_shouldDeleteAndReturnNull() {
        UserSession session = new UserSession();
        session.setToken("expired");
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectByToken("expired")).thenReturn(session);

        assertNull(tokenService.validate("expired"));
        verify(sessionMapper).deleteByToken("expired");
    }

    @Test
    void validate_unknownToken_shouldReturnNull() {
        when(sessionMapper.selectByToken("none")).thenReturn(null);
        assertNull(tokenService.validate("none"));
    }

    @Test
    void validate_blankToken_shouldReturnNullWithoutQuery() {
        assertNull(tokenService.validate("  "));
        verify(sessionMapper, never()).selectByToken(anyString());
    }

    @Test
    void revoke_shouldDeleteToken() {
        tokenService.revoke("abc");
        verify(sessionMapper).deleteByToken("abc");
    }

    @Test
    void revoke_blankToken_shouldDoNothing() {
        tokenService.revoke("");
        verify(sessionMapper, never()).deleteByToken(anyString());
    }

    @Test
    void requireSession_invalid_shouldThrowUnauthorized() {
        when(sessionMapper.selectByToken("bad")).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> tokenService.requireSession("bad"));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), error.getCode());
    }
}
