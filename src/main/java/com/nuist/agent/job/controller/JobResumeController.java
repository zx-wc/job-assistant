package com.nuist.agent.job.controller;

import com.nuist.agent.entity.JobResume;
import com.nuist.agent.entity.JobResumeVersion;
import com.nuist.agent.zhangxu.entity.R;
import com.nuist.agent.zhangxu.auth.UserContextHolder;
import com.nuist.agent.zhangxu.dto.JobDtos;
import com.nuist.agent.zhangxu.dto.ViewDtos;
import com.nuist.agent.job.resume.JobResumeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历资料管理接口（成员 B）。
 * 用户 id 一律取自登录上下文（{@link UserContextHolder}），前端无法通过参数越权。
 */
@RestController
@RequestMapping("/api/job/resumes")
public class JobResumeController {

    private final JobResumeService resumeService;

    public JobResumeController(JobResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /** 简历分页列表 */
    @GetMapping
    public R page(@RequestParam(required = false) String keyword,
                  @RequestParam(defaultValue = "1") int page,
                  @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContextHolder.requireUserId();
        ViewDtos.PageResult<JobResume> result = resumeService.page(userId, keyword, page, size);
        return R.ok().data("page", result);
    }

    /** 新建简历 */
    @PostMapping
    public R create(@RequestBody(required = false) JobDtos.ResumeSaveRequest request) {
        Long userId = UserContextHolder.requireUserId();
        JobResume resume = resumeService.create(userId, request);
        return R.ok().data("resume", resume);
    }

    /** 简历详情（含正文） */
    @GetMapping("/{id}")
    public R detail(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("resume", resumeService.detail(userId, id));
    }

    /** 编辑简历 */
    @PutMapping("/{id}")
    public R update(@PathVariable Long id, @RequestBody(required = false) JobDtos.ResumeSaveRequest request) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("resume", resumeService.update(userId, id, request));
    }

    /** 删除简历（逻辑删除） */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        resumeService.delete(userId, id);
        return R.ok();
    }

    /** 版本列表 */
    @GetMapping("/{id}/versions")
    public R versions(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        List<JobResumeVersion> versions = resumeService.versions(userId, id);
        return R.ok().data("versions", versions);
    }

    /** 指定版本详情 */
    @GetMapping("/{id}/versions/{versionNo}")
    public R versionDetail(@PathVariable Long id, @PathVariable int versionNo) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("version", resumeService.versionDetail(userId, id, versionNo));
    }

    /** 手动保存一个版本 */
    @PostMapping("/{id}/versions")
    public R saveVersion(@PathVariable Long id,
                         @RequestBody(required = false) JobDtos.VersionSaveRequest request) {
        Long userId = UserContextHolder.requireUserId();
        String note = request == null ? null : request.getChangeNote();
        return R.ok().data("version", resumeService.saveVersion(userId, id, note));
    }

    /** 回滚到指定版本 */
    @PostMapping("/{id}/versions/{versionNo}/restore")
    public R restore(@PathVariable Long id, @PathVariable int versionNo) {
        Long userId = UserContextHolder.requireUserId();
        JobResume resume = resumeService.restoreVersion(userId, id, versionNo);
        return R.ok().data("resume", resume);
    }

    /** 上传 txt/md 文本，返回脱敏后的正文（前端填充到编辑框，不直接落库） */
    @PostMapping("/upload-text")
    public R uploadText(@RequestPart(value = "file", required = false) MultipartFile file) {
        UserContextHolder.requireUserId();
        String content = resumeService.importText(file);
        return R.ok().data("content", content).data("length", content.length());
    }
}
