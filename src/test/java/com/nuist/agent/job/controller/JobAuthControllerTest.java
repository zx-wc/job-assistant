package com.nuist.agent.job.controller;

import com.nuist.agent.exception.GlobalExceptionHandler;
import com.nuist.agent.job.auth.JobAuthService;
import com.nuist.agent.job.dto.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户与权限接口测试（成员 A 接口 + 成员 E 接口测试）。
 * 使用 standalone MockMvc + Mockito，不依赖 Spring 容器与数据库。
 */
class JobAuthControllerTest {

    private JobAuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(JobAuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new JobAuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    void login_success_shouldReturnTokenAndUser() throws Exception {
        when(authService.login(any())).thenReturn(authVO());

        mockMvc.perform(post("/api/job/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jobuser\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.dataMap.token").value("token-abc"))
                .andExpect(jsonPath("$.dataMap.user.username").value("jobuser"))
                .andExpect(jsonPath("$.dataMap.user.role").value("USER"))
                .andExpect(jsonPath("$.dataMap.expireTime").value("2026-01-08 00:00:00"));
    }

    @Test
    void login_withNullBody_shouldReturnParameterError() throws Exception {
        mockMvc.perform(post("/api/job/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100));
    }

    @Test
    void register_shouldPassAllFields() throws Exception {
        when(authService.register(any())).thenReturn(authVO());

        mockMvc.perform(post("/api/job/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"abcdef\",\"nickname\":\"小新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.dataMap.token").value("token-abc"));

        org.mockito.ArgumentCaptor<AuthDtos.RegisterRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AuthDtos.RegisterRequest.class);
        verify(authService).register(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("newuser", captor.getValue().getUsername());
        org.junit.jupiter.api.Assertions.assertEquals("小新", captor.getValue().getNickname());
    }

    @Test
    void me_shouldReturnCurrentUser() throws Exception {
        AuthDtos.UserVO user = new AuthDtos.UserVO();
        user.setId(1L);
        user.setUsername("jobuser");
        user.setRole("USER");
        when(authService.currentUser()).thenReturn(user);

        mockMvc.perform(get("/api/job/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.user.username").value("jobuser"));
    }

    @Test
    void logout_shouldPassTokenFromHeader() throws Exception {
        mockMvc.perform(post("/api/job/auth/logout").header("Authorization", "Bearer token-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(authService).logout("token-xyz");
    }

    @Test
    void logout_shouldAcceptXAuthTokenHeader() throws Exception {
        mockMvc.perform(post("/api/job/auth/logout").header("X-Auth-Token", "token-2"))
                .andExpect(status().isOk());
        verify(authService).logout("token-2");
    }

    @Test
    void ping_shouldBeOkWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/job/auth/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.dataMap.status").value("UP"));
    }

    private AuthDtos.AuthVO authVO() {
        AuthDtos.UserVO user = new AuthDtos.UserVO();
        user.setId(1L);
        user.setUsername("jobuser");
        user.setNickname("演示求职者");
        user.setRole("USER");
        AuthDtos.AuthVO vo = new AuthDtos.AuthVO();
        vo.setUser(user);
        vo.setToken("token-abc");
        vo.setExpireTime("2026-01-08 00:00:00");
        return vo;
    }
}
