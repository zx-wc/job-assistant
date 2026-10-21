package com.nuist.agent.job.analysis;

import com.nuist.agent.zhangxu.dto.ViewDtos;

/**
 * 简历与岗位分析服务（成员 B）。
 * 调用 AI 网关输出结构化改进建议；大模型不可用时自动切换本地规则降级分析。
 */
public interface ResumeAnalysisService {

    /** 对指定简历与岗位执行分析并落库 */
    ViewDtos.AnalysisVO analyze(Long userId, Long resumeId, Long positionId);

    /** 报告详情 */
    ViewDtos.AnalysisVO detail(Long userId, Long analysisId);

    /** 报告分页列表 */
    ViewDtos.PageResult<ViewDtos.AnalysisVO> page(Long userId, int page, int size);

    /** 删除报告 */
    void delete(Long userId, Long analysisId);

    /** 当前用户报告总数 */
    int countByUser(Long userId);
}
