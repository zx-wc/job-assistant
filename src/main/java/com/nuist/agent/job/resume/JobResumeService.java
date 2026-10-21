package com.nuist.agent.job.resume;

import com.nuist.agent.entity.JobResume;
import com.nuist.agent.entity.JobResumeVersion;
import com.nuist.agent.job.dto.JobDtos;
import com.nuist.agent.job.dto.ViewDtos;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历资料管理服务（成员 B）。
 * 覆盖需求：文本录入、文本文件上传、增删改查、版本保存与回溯。
 */
public interface JobResumeService {

    /** 新建简历（同时生成 v1 版本快照） */
    JobResume create(Long userId, JobDtos.ResumeSaveRequest request);

    /** 编辑简历（正文变化时自动留存版本） */
    JobResume update(Long userId, Long id, JobDtos.ResumeSaveRequest request);

    /** 简历详情（含正文） */
    JobResume detail(Long userId, Long id);

    /** 分页查询（关键词匹配标题/目标岗位/姓名） */
    ViewDtos.PageResult<JobResume> page(Long userId, String keyword, int page, int size);

    /** 逻辑删除 */
    void delete(Long userId, Long id);

    /** 版本列表（不含正文） */
    List<JobResumeVersion> versions(Long userId, Long id);

    /** 指定版本详情（含正文） */
    JobResumeVersion versionDetail(Long userId, Long id, int versionNo);

    /** 手动保存一个版本快照 */
    JobResumeVersion saveVersion(Long userId, Long id, String changeNote);

    /** 回滚到指定版本（生成新版本记录，历史保持只增不改） */
    JobResume restoreVersion(Long userId, Long id, int versionNo);

    /** 读取上传的 txt/md 文本内容（用于前端填充正文，不直接落库） */
    String importText(MultipartFile file);
}
