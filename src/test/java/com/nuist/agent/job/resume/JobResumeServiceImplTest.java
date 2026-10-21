package com.nuist.agent.job.resume;

import com.nuist.agent.zhangxu.common.ErrorCode;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.entity.JobResumeVersion;
import com.nuist.agent.zhangxu.exception.BusinessException;
import com.nuist.agent.zhangxu.common.PrivacyMasker;
import com.nuist.agent.zhangxu.config.JobProperties;
import com.nuist.agent.zhangxu.dto.JobDtos;
import com.nuist.agent.zhangxu.dto.ViewDtos;
import com.nuist.agent.zhangxu.rag.JobVectorService;
import com.nuist.agent.mapper.JobResumeMapper;
import com.nuist.agent.mapper.JobResumeVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 简历资料管理测试（成员 B 模块 + 成员 E 测试）。
 * 覆盖：创建（含脱敏与初始版本）、编辑自动留版本、正文未变不留版本、分页参数归一化、
 * 越权/不存在校验、版本查看与回滚、文本导入的格式与大小校验。
 */
class JobResumeServiceImplTest {

    private JobResumeMapper resumeMapper;
    private JobResumeVersionMapper versionMapper;
    private JobResumeServiceImpl service;

    @BeforeEach
    void setUp() {
        resumeMapper = mock(JobResumeMapper.class);
        versionMapper = mock(JobResumeVersionMapper.class);
        service = new JobResumeServiceImpl(resumeMapper, versionMapper, new JobProperties(),
                new PrivacyMasker(), mock(JobVectorService.class));
    }

    @Test
    void create_shouldMaskContentAndCreateFirstVersion() {
        when(resumeMapper.insert(any(JobResume.class))).thenAnswer(invocation -> {
            JobResume resume = invocation.getArgument(0);
            resume.setId(10L);
            return 1;
        });

        JobDtos.ResumeSaveRequest request = new JobDtos.ResumeSaveRequest();
        request.setTitle("Java 后端-秋招版");
        request.setCandidateName("张同学");
        request.setTargetPosition("Java 后端");
        request.setContent("联系方式 13812345678，邮箱 zhang@test.com\n项目经历：订单模块");

        JobResume created = service.create(1L, request);

        assertEquals(10L, created.getId());
        assertEquals(1, created.getCurrentVersion());
        assertEquals(1L, created.getUserId());
        assertTrue(created.getContent().contains("138****5678"));
        assertFalse(created.getContent().contains("13812345678"));

        ArgumentCaptor<JobResumeVersion> captor = ArgumentCaptor.forClass(JobResumeVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getVersionNo());
        assertEquals("初始版本", captor.getValue().getChangeNote());
    }

    @Test
    void create_blankContent_shouldThrow() {
        JobDtos.ResumeSaveRequest request = new JobDtos.ResumeSaveRequest();
        request.setContent("   ");
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(1L, request));
        assertEquals(ErrorCode.RESUME_CONTENT_EMPTY.getCode(), error.getCode());
        verify(resumeMapper, never()).insert(any());
    }

    @Test
    void create_nullRequest_shouldThrow() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(1L, null));
        assertEquals(ErrorCode.PARAMETER_ERROR.getCode(), error.getCode());
    }

    @Test
    void create_contentTooLong_shouldThrow() {
        JobProperties properties = new JobProperties();
        properties.getResume().setMaxContentLength(10);
        JobResumeServiceImpl limited = new JobResumeServiceImpl(resumeMapper, versionMapper, properties,
                new PrivacyMasker(), mock(JobVectorService.class));
        JobDtos.ResumeSaveRequest request = new JobDtos.ResumeSaveRequest();
        request.setContent("这是一段明显超过十个字符的简历正文");
        BusinessException error = assertThrows(BusinessException.class, () -> limited.create(1L, request));
        assertTrue(error.getMessage().contains("长度限制"));
    }

    @Test
    void create_withoutTitle_shouldUseDefaultTitle() {
        when(resumeMapper.insert(any(JobResume.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, JobResume.class).setId(11L);
            return 1;
        });
        JobDtos.ResumeSaveRequest request = new JobDtos.ResumeSaveRequest();
        request.setContent("正文内容");
        assertEquals("未命名简历", service.create(1L, request).getTitle());
    }

    @Test
    void update_contentChanged_shouldCreateNewVersion() {
        JobResume existing = resume(10L, "旧正文");
        when(resumeMapper.selectById(10L, 1L)).thenReturn(existing);
        when(resumeMapper.update(any(JobResume.class))).thenReturn(1);
        when(versionMapper.selectMaxVersionNo(10L)).thenReturn(1);

        JobDtos.ResumeSaveRequest request = new JobDtos.ResumeSaveRequest();
        request.setContent("新的正文内容");

        service.update(1L, 10L, request);

        verify(resumeMapper).updateCurrentVersion(10L, 1L, 2);
        ArgumentCaptor<JobResumeVersion> captor = ArgumentCaptor.forClass(JobResumeVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getVersionNo());
        assertEquals("编辑自动保存", captor.getValue().getChangeNote());
    }

    @Test
    void update_contentUnchanged_shouldNotCreateVersion() {
        JobResume existing = resume(10L, "同一段正文");
        when(resumeMapper.selectById(10L, 1L)).thenReturn(existing);
        when(resumeMapper.update(any(JobResume.class))).thenReturn(1);

        JobDtos.ResumeSaveRequest request = new JobDtos.ResumeSaveRequest();
        request.setContent("同一段正文");
        request.setTitle("新标题");

        service.update(1L, 10L, request);

        verify(versionMapper, never()).insert(any());
        verify(resumeMapper, never()).updateCurrentVersion(anyLong(), anyLong(), anyInt());
    }

    @Test
    void update_missingResume_shouldThrow() {
        when(resumeMapper.selectById(99L, 1L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, 99L, new JobDtos.ResumeSaveRequest()));
        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void update_rowsZero_shouldThrowNotFound() {
        when(resumeMapper.selectById(10L, 1L)).thenReturn(resume(10L, "旧"));
        when(resumeMapper.update(any(JobResume.class))).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.update(1L, 10L, new JobDtos.ResumeSaveRequest()));
    }

    @Test
    void page_shouldNormalizeSizeAndOffset() {
        when(resumeMapper.selectByPage(eq(1L), isNull(), eq(0), eq(50))).thenReturn(List.of());
        when(resumeMapper.countByPage(1L, null)).thenReturn(0);

        ViewDtos.PageResult<JobResume> page = service.page(1L, null, 0, 999);

        assertEquals(1, page.getPage());
        assertEquals(50, page.getSize());
        assertEquals(0, page.getTotal());
    }

    @Test
    void page_keywordShouldBeTrimmed() {
        when(resumeMapper.selectByPage(1L, "Java", 0, 10)).thenReturn(List.of());
        when(resumeMapper.countByPage(1L, "Java")).thenReturn(0);
        service.page(1L, "  Java  ", 1, 10);
        verify(resumeMapper).selectByPage(1L, "Java", 0, 10);
    }

    @Test
    void detail_missing_shouldThrow() {
        when(resumeMapper.selectById(5L, 1L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.detail(1L, 5L));
        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void delete_rowsZero_shouldThrow() {
        when(resumeMapper.softDelete(5L, 1L)).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.delete(1L, 5L));
    }

    @Test
    void delete_success_shouldCallMapper() {
        when(resumeMapper.softDelete(5L, 1L)).thenReturn(1);
        service.delete(1L, 5L);
        verify(resumeMapper).softDelete(5L, 1L);
    }

    @Test
    void versionDetail_missing_shouldThrow() {
        when(resumeMapper.selectById(10L, 1L)).thenReturn(resume(10L, "x"));
        when(versionMapper.selectByVersionNo(10L, 3)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.versionDetail(1L, 10L, 3));
        assertEquals(ErrorCode.VERSION_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void saveVersion_shouldIncreaseVersionNo() {
        when(resumeMapper.selectById(10L, 1L)).thenReturn(resume(10L, "内容"));
        when(versionMapper.selectMaxVersionNo(10L)).thenReturn(3);

        JobResumeVersion version = service.saveVersion(1L, 10L, "补充实习经历");

        assertEquals(4, version.getVersionNo());
        assertEquals("补充实习经历", version.getChangeNote());
        verify(resumeMapper).updateCurrentVersion(10L, 1L, 4);
    }

    @Test
    void saveVersion_withoutNote_shouldUseDefault() {
        when(resumeMapper.selectById(10L, 1L)).thenReturn(resume(10L, "内容"));
        when(versionMapper.selectMaxVersionNo(10L)).thenReturn(null);
        assertEquals("手动保存版本", service.saveVersion(1L, 10L, " ").getChangeNote());
    }

    @Test
    void restoreVersion_shouldCopySnapshotAndCreateNewVersion() {
        when(resumeMapper.selectById(10L, 1L)).thenReturn(resume(10L, "当前正文"));
        JobResumeVersion v1 = new JobResumeVersion();
        v1.setResumeId(10L);
        v1.setVersionNo(1);
        v1.setTitle("旧标题");
        v1.setContent("v1 正文");
        when(versionMapper.selectByVersionNo(10L, 1)).thenReturn(v1);
        when(versionMapper.selectMaxVersionNo(10L)).thenReturn(2);
        when(resumeMapper.update(any(JobResume.class))).thenReturn(1);

        service.restoreVersion(1L, 10L, 1);

        ArgumentCaptor<JobResume> resumeCaptor = ArgumentCaptor.forClass(JobResume.class);
        verify(resumeMapper).update(resumeCaptor.capture());
        assertEquals("v1 正文", resumeCaptor.getValue().getContent());
        assertEquals("旧标题", resumeCaptor.getValue().getTitle());

        ArgumentCaptor<JobResumeVersion> versionCaptor = ArgumentCaptor.forClass(JobResumeVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertEquals(3, versionCaptor.getValue().getVersionNo());
        assertTrue(versionCaptor.getValue().getChangeNote().contains("回滚"));
    }

    @Test
    void importText_txtFile_shouldMaskAndReturn() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain",
                "电话 13812345678\n项目经历".getBytes(StandardCharsets.UTF_8));
        String content = service.importText(file);
        assertTrue(content.contains("138****5678"));
    }

    @Test
    void importText_markdownFile_shouldWork() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.MD", "text/markdown",
                "# 简历\n项目经历".getBytes(StandardCharsets.UTF_8));
        assertTrue(service.importText(file).contains("项目经历"));
    }

    @Test
    void importText_wrongExtension_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf",
                "内容".getBytes(StandardCharsets.UTF_8));
        BusinessException error = assertThrows(BusinessException.class, () -> service.importText(file));
        assertEquals(ErrorCode.FILE_TYPE_ERROR.getCode(), error.getCode());
    }

    @Test
    void importText_emptyFile_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", new byte[0]);
        BusinessException error = assertThrows(BusinessException.class, () -> service.importText(file));
        assertEquals(ErrorCode.FILE_EMPTY.getCode(), error.getCode());
    }

    @Test
    void importText_nullFile_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.importText(null));
    }

    @Test
    void importText_tooLarge_shouldThrow() {
        JobProperties properties = new JobProperties();
        properties.getUpload().setMaxFileSize(4);
        JobResumeServiceImpl limited = new JobResumeServiceImpl(resumeMapper, versionMapper, properties,
                new PrivacyMasker(), mock(JobVectorService.class));
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain",
                "更长的内容".getBytes(StandardCharsets.UTF_8));
        BusinessException error = assertThrows(BusinessException.class, () -> limited.importText(file));
        assertEquals(ErrorCode.FILE_EMPTY.getCode(), error.getCode());
    }

    private JobResume resume(Long id, String content) {
        JobResume resume = new JobResume();
        resume.setId(id);
        resume.setUserId(1L);
        resume.setTitle("简历");
        resume.setContent(content);
        resume.setStatus(1);
        resume.setCurrentVersion(1);
        return resume;
    }
}
