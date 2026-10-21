package com.nuist.agent.job.controller;

import com.nuist.agent.entity.R;
import com.nuist.agent.job.auth.UserContextHolder;
import com.nuist.agent.job.dashboard.JobDashboardService;
import com.nuist.agent.job.dto.ViewDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 概览接口（成员 D 首页）。
 */
@RestController
@RequestMapping("/api/job/dashboard")
public class JobDashboardController {

    private final JobDashboardService dashboardService;

    public JobDashboardController(JobDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 当前用户的求职概览 */
    @GetMapping
    public R load() {
        Long userId = UserContextHolder.requireUserId();
        ViewDtos.DashboardVO vo = dashboardService.load(userId);
        return R.ok().data("dashboard", vo);
    }
}
