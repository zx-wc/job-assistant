package com.nuist.agent.job.position;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.exception.BusinessException;
import com.nuist.agent.job.common.PrivacyMasker;
import com.nuist.agent.job.dto.JobDtos;
import com.nuist.agent.job.dto.ViewDtos;
import com.nuist.agent.job.rag.JobVectorService;
import com.nuist.agent.mapper.JobPositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 岗位信息管理测试（成员 B 模块 + 成员 E 测试）。
 */
class JobPositionServiceImplTest {

    private JobPositionMapper positionMapper;
    private JobPositionServiceImpl service;

    @BeforeEach
    void setUp() {
        positionMapper = mock(JobPositionMapper.class);
        service = new JobPositionServiceImpl(positionMapper, new PrivacyMasker(), mock(JobVectorService.class));
    }

    @Test
    void create_shouldMaskJobDesc() {
        when(positionMapper.insert(any(JobPosition.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, JobPosition.class).setId(3L);
            return 1;
        });
        JobDtos.PositionSaveRequest request = new JobDtos.PositionSaveRequest();
        request.setPosition("Java 后端开发工程师");
        request.setCompany("某公司");
        request.setJobDesc("投递邮箱 hr@example.com");
        request.setRequirements("熟悉 Java");

        JobPosition created = service.create(1L, request);

        assertEquals(3L, created.getId());
        assertEquals(1L, created.getUserId());
        assertTrue(created.getJobDesc().contains("hr***@example.com"));
    }

    @Test
    void create_blankPosition_shouldThrow() {
        JobDtos.PositionSaveRequest request = new JobDtos.PositionSaveRequest();
        request.setPosition("  ");
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(1L, request));
        assertEquals(ErrorCode.PARAMETER_ERROR.getCode(), error.getCode());
        verify(positionMapper, never()).insert(any());
    }

    @Test
    void create_nullRequest_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.create(1L, null));
    }

    @Test
    void update_missingPosition_shouldThrow() {
        when(positionMapper.selectById(9L, 1L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, 9L, new JobDtos.PositionSaveRequest()));
        assertEquals(ErrorCode.POSITION_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void update_shouldKeepUnsetFields() {
        JobPosition existing = position(5L);
        when(positionMapper.selectById(5L, 1L)).thenReturn(existing);
        when(positionMapper.update(any(JobPosition.class))).thenReturn(1);
        when(positionMapper.selectById(5L, 1L)).thenReturn(existing);

        JobDtos.PositionSaveRequest request = new JobDtos.PositionSaveRequest();
        request.setPosition("高级 Java 后端");

        service.update(1L, 5L, request);

        ArgumentCaptor<JobPosition> captor = ArgumentCaptor.forClass(JobPosition.class);
        verify(positionMapper).update(captor.capture());
        assertEquals("高级 Java 后端", captor.getValue().getPosition());
        assertEquals("某公司", captor.getValue().getCompany());
        assertEquals("南京", captor.getValue().getCity());
    }

    @Test
    void update_rowsZero_shouldThrow() {
        when(positionMapper.selectById(5L, 1L)).thenReturn(position(5L));
        when(positionMapper.update(any(JobPosition.class))).thenReturn(0);
        assertThrows(BusinessException.class,
                () -> service.update(1L, 5L, new JobDtos.PositionSaveRequest()));
    }

    @Test
    void page_shouldClampSize() {
        when(positionMapper.selectByPage(eq(1L), isNull(), eq(0), eq(50))).thenReturn(List.of());
        when(positionMapper.countByPage(1L, null)).thenReturn(0);

        ViewDtos.PageResult<JobPosition> page = service.page(1L, null, 0, 500);

        assertEquals(50, page.getSize());
        assertEquals(1, page.getPage());
    }

    @Test
    void page_secondPageOffsetShouldBeCorrect() {
        when(positionMapper.selectByPage(1L, null, 20, 10)).thenReturn(List.of());
        when(positionMapper.countByPage(1L, null)).thenReturn(21);
        service.page(1L, null, 3, 10);
        verify(positionMapper).selectByPage(1L, null, 20, 10);
    }

    @Test
    void delete_rowsZero_shouldThrow() {
        when(positionMapper.softDelete(8L, 1L)).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(1L, 8L));
        assertEquals(ErrorCode.POSITION_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void detail_missing_shouldThrow() {
        when(positionMapper.selectById(8L, 1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.detail(1L, 8L));
    }

    @Test
    void countByUser_shouldDelegate() {
        when(positionMapper.countByUser(1L)).thenReturn(4);
        assertEquals(4, service.countByUser(1L));
    }

    private JobPosition position(Long id) {
        JobPosition position = new JobPosition();
        position.setId(id);
        position.setUserId(1L);
        position.setPosition("Java 后端");
        position.setCompany("某公司");
        position.setCity("南京");
        position.setJobDesc("负责后端开发");
        position.setRequirements("熟悉 Spring Boot");
        position.setStatus(1);
        return position;
    }
}
