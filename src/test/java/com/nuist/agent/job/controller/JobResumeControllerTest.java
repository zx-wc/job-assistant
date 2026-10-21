package com.nuist.agent.job.controller;

import com.nuist.agent.entity.JobResume;
import com.nuist.agent.entity.JobResumeVersion;
import com.nuist.agent.zhangxu.exception.GlobalExceptionHandler;
import com.nuist.agent.zhangxu.auth.LoginUser;
import com.nuist.agent.zhangxu.auth.UserContextHolder;
import com.nuist.agent.zhangxu.dto.ViewDtos;
import com.nuist.agent.job.resume.JobResumeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 简历管理接口测试（成员 B 接口 + 成员 E 接口测试）。
 * 通过 UserContextHolder 模拟登录用户，验证接口契约与用户隔离。
 */
class JobResumeControllerTest {

    private JobResumeService resumeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        resumeService = mock(JobResumeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new JobResumeController(resumeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
        UserContextHolder.set(new LoginUser(1L, "jobuser", "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void page_shouldReturnPageResult() throws Exception {
        ViewDtos.PageResult<JobResume> page = ViewDtos.PageResult.of(List.of(resume()), 1, 1, 10);
        when(resumeService.page(eq(1L), isNull(), eq(1), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/job/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.dataMap.page.total").value(1))
                .andExpect(jsonPath("$.dataMap.page.list[0].title").value("后端简历"));
    }

    @Test
    void page_withKeywordAndPaging() throws Exception {
        when(resumeService.page(1L, "Java", 2, 5)).thenReturn(ViewDtos.PageResult.of(List.of(), 0, 2, 5));

        mockMvc.perform(get("/api/job/resumes").param("keyword", "Java").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.page.page").value(2));
    }

    @Test
    void create_shouldUseLoginUserId() throws Exception {
        when(resumeService.create(eq(1L), any())).thenReturn(resume());

        mockMvc.perform(post("/api/job/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"后端简历\",\"content\":\"项目经历\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.resume.title").value("后端简历"));

        verify(resumeService).create(eq(1L), any());
    }

    @Test
    void detail_shouldReturnResume() throws Exception {
        when(resumeService.detail(1L, 10L)).thenReturn(resume());

        mockMvc.perform(get("/api/job/resumes/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.resume.id").value(10));
    }

    @Test
    void update_shouldPassIdAndBody() throws Exception {
        when(resumeService.update(eq(1L), eq(10L), any())).thenReturn(resume());

        mockMvc.perform(put("/api/job/resumes/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\",\"content\":\"新正文\"}"))
                .andExpect(status().isOk());

        verify(resumeService).update(eq(1L), eq(10L), any());
    }

    @Test
    void delete_shouldCallService() throws Exception {
        mockMvc.perform(delete("/api/job/resumes/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(resumeService).delete(1L, 10L);
    }

    @Test
    void versions_shouldReturnList() throws Exception {
        JobResumeVersion version = new JobResumeVersion();
        version.setVersionNo(1);
        version.setChangeNote("初始版本");
        when(resumeService.versions(1L, 10L)).thenReturn(List.of(version));

        mockMvc.perform(get("/api/job/resumes/10/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.versions[0].versionNo").value(1));
    }

    @Test
    void versionDetail_shouldReturnContent() throws Exception {
        JobResumeVersion version = new JobResumeVersion();
        version.setVersionNo(2);
        version.setContent("v2 正文");
        when(resumeService.versionDetail(1L, 10L, 2)).thenReturn(version);

        mockMvc.perform(get("/api/job/resumes/10/versions/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.version.content").value("v2 正文"));
    }

    @Test
    void saveVersion_shouldPassChangeNote() throws Exception {
        JobResumeVersion version = new JobResumeVersion();
        version.setVersionNo(3);
        when(resumeService.saveVersion(1L, 10L, "补充实习")).thenReturn(version);

        mockMvc.perform(post("/api/job/resumes/10/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeNote\":\"补充实习\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.version.versionNo").value(3));
    }

    @Test
    void restore_shouldReturnUpdatedResume() throws Exception {
        when(resumeService.restoreVersion(1L, 10L, 1)).thenReturn(resume());

        mockMvc.perform(post("/api/job/resumes/10/versions/1/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(resumeService).restoreVersion(1L, 10L, 1);
    }

    @Test
    void uploadText_shouldReturnMaskedContent() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain",
                "电话 13812345678".getBytes(StandardCharsets.UTF_8));
        when(resumeService.importText(any())).thenReturn("电话 138****5678");

        mockMvc.perform(multipart("/api/job/resumes/upload-text").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMap.content").value("电话 138****5678"))
                .andExpect(jsonPath("$.dataMap.length").value("电话 138****5678".length()));
    }

    @Test
    void withoutLogin_shouldReturnUnauthorized() throws Exception {
        UserContextHolder.clear();
        mockMvc.perform(get("/api/job/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    private JobResume resume() {
        JobResume resume = new JobResume();
        resume.setId(10L);
        resume.setUserId(1L);
        resume.setTitle("后端简历");
        resume.setTargetPosition("Java 后端");
        resume.setContent("项目经历");
        resume.setStatus(1);
        resume.setCurrentVersion(1);
        return resume;
    }
}
