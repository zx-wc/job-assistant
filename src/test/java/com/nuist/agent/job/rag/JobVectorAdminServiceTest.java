package com.nuist.agent.job.rag;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.mapper.JobPositionMapper;
import com.nuist.agent.mapper.JobResumeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 向量索引重建服务测试（成员 A）。
 */
class JobVectorAdminServiceTest {

    private JobVectorService vectorService;
    private JobResumeMapper resumeMapper;
    private JobPositionMapper positionMapper;
    private JobVectorAdminService adminService;

    @BeforeEach
    void setUp() {
        vectorService = mock(JobVectorService.class);
        resumeMapper = mock(JobResumeMapper.class);
        positionMapper = mock(JobPositionMapper.class);
        adminService = new JobVectorAdminService(vectorService, resumeMapper, positionMapper);
    }

    @Test
    void reindexUser_shouldIndexAllResumesAndPositions() {
        when(vectorService.available()).thenReturn(true);
        JobResume resume = new JobResume();
        resume.setId(1L);
        JobPosition position = new JobPosition();
        position.setId(2L);
        when(resumeMapper.selectAllByUser(5L)).thenReturn(List.of(resume));
        when(positionMapper.selectAllByUser(5L)).thenReturn(List.of(position));

        JobVectorAdminService.ReindexResult result = adminService.reindexUser(5L);

        assertTrue(result.available());
        assertEquals(1, result.resumes());
        assertEquals(1, result.positions());
        verify(vectorService).indexResume(resume);
        verify(vectorService).indexPosition(position);
    }

    @Test
    void reindexUser_milvusDown_shouldSkip() {
        when(vectorService.available()).thenReturn(false);

        JobVectorAdminService.ReindexResult result = adminService.reindexUser(5L);

        assertFalse(result.available());
        assertEquals(0, result.resumes());
        assertEquals("Milvus 当前不可用", result.message());
        verify(vectorService, never()).indexResume(org.mockito.ArgumentMatchers.any());
    }
}
