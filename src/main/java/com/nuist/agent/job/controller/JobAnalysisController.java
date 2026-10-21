package com.nuist.agent.job.controller;

import com.nuist.agent.common.ErrorCode;
import com.nuist.agent.entity.R;
import com.nuist.agent.job.analysis.ResumeAnalysisService;
import com.nuist.agent.job.auth.UserContextHolder;
import com.nuist.agent.job.dto.JobDtos;
import com.nuist.agent.job.dto.ViewDtos;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历与岗位分析接口（成员 B）。
 */
@RestController
@RequestMapping("/api/job/analyses")
public class JobAnalysisController {

    private final ResumeAnalysisService analysisService;

    public JobAnalysisController(ResumeAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /** 创建分析：{resumeId, positionId} → 结构化分析报告 */
    @PostMapping
    public R analyze(@RequestBody(required = false) JobDtos.AnalysisCreateRequest request) {
        Long userId = UserContextHolder.requireUserId();
        if (request == null || request.getResumeId() == null || request.getPositionId() == null) {
            return R.error(ErrorCode.PARAMETER_ERROR.getCode(), "resumeId 与 positionId 不能为空");
        }
        ViewDtos.AnalysisVO vo = analysisService.analyze(userId, request.getResumeId(), request.getPositionId());
        return R.ok().data("analysis", vo);
    }

    /** 报告分页列表 */
    @GetMapping
    public R page(@RequestParam(defaultValue = "1") int page,
                  @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("page", analysisService.page(userId, page, size));
    }

    /** 报告详情 */
    @GetMapping("/{id}")
    public R detail(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("analysis", analysisService.detail(userId, id));
    }

    /** 删除报告 */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        analysisService.delete(userId, id);
        return R.ok();
    }
}
