package com.nuist.agent.job.dashboard;

import com.nuist.agent.job.analysis.ResumeAnalysisService;
import com.nuist.agent.job.dto.ViewDtos;
import com.nuist.agent.job.interview.InterviewService;
import com.nuist.agent.job.position.JobPositionService;
import com.nuist.agent.job.resume.JobResumeService;
import com.nuist.agent.mapper.JobAnalysisMapper;
import org.springframework.stereotype.Service;

/**
 * 学习/求职概览（成员 D 的前端首页数据来源）。
 * 汇总简历数、岗位数、分析数、面试数、最近报告与记录，避免前端多次请求。
 */
@Service
public class JobDashboardService {

    private final JobResumeService resumeService;
    private final JobPositionService positionService;
    private final ResumeAnalysisService analysisService;
    private final InterviewService interviewService;
    private final JobAnalysisMapper analysisMapper;

    public JobDashboardService(JobResumeService resumeService,
                               JobPositionService positionService,
                               ResumeAnalysisService analysisService,
                               InterviewService interviewService,
                               JobAnalysisMapper analysisMapper) {
        this.resumeService = resumeService;
        this.positionService = positionService;
        this.analysisService = analysisService;
        this.interviewService = interviewService;
        this.analysisMapper = analysisMapper;
    }

    /** 汇总当前用户概览数据 */
    public ViewDtos.DashboardVO load(Long userId) {
        ViewDtos.DashboardVO vo = new ViewDtos.DashboardVO();
        ViewDtos.PageResult<com.nuist.agent.entity.JobResume> resumePage = resumeService.page(userId, null, 1, 1);
        vo.setResumeCount((int) resumePage.getTotal());
        vo.setPositionCount(positionService.countByUser(userId));
        vo.setAnalysisCount(analysisService.countByUser(userId));
        vo.setInterviewCount(interviewService.countByUser(userId));
        vo.setOngoingInterviewCount(interviewService.countOngoing(userId));
        vo.setLatestMatchScore(analysisMapper.selectLatestMatchScore(userId));
        vo.setRecentAnalyses(analysisService.page(userId, 1, 3).getList());
        vo.setRecentInterviews(interviewService.page(userId, null, 1, 3).getList());
        return vo;
    }
}
