package com.nuist.agent.job.position;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.job.dto.JobDtos;
import com.nuist.agent.job.dto.ViewDtos;

/**
 * 岗位信息管理服务（成员 B）。
 * 覆盖需求：岗位录入、编辑、删除、分页查询、详情。
 */
public interface JobPositionService {

    JobPosition create(Long userId, JobDtos.PositionSaveRequest request);

    JobPosition update(Long userId, Long id, JobDtos.PositionSaveRequest request);

    JobPosition detail(Long userId, Long id);

    ViewDtos.PageResult<JobPosition> page(Long userId, String keyword, int page, int size);

    void delete(Long userId, Long id);

    /** 当前用户的岗位总数（概览页） */
    int countByUser(Long userId);
}
