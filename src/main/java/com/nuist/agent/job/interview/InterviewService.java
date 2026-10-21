package com.nuist.agent.job.interview;

import com.nuist.agent.job.dto.JobDtos;
import com.nuist.agent.job.dto.ViewDtos;

/**
 * 文字模拟面试服务（成员 C）。
 * 覆盖需求：围绕目标岗位生成问题、逐题作答与单题点评、多轮追问（选做）、
 * 结束生成反馈报告、训练记录保存与回顾。
 */
public interface InterviewService {

    /** 开始一场模拟面试（生成整套题目） */
    ViewDtos.SessionVO start(Long userId, JobDtos.InterviewStartRequest request);

    /** 会话详情（含题目与报告） */
    ViewDtos.SessionVO detail(Long userId, Long sessionId);

    /** 提交单题作答并返回评分与下一题 */
    ViewDtos.AnswerResultVO answer(Long userId, Long sessionId, Integer questionNo, String answer);

    /** 对已答题目追加一次追问（多轮追问） */
    ViewDtos.SessionVO followUp(Long userId, Long sessionId, Integer questionNo, String extra);

    /** 结束面试并生成反馈报告 */
    ViewDtos.ReportVO finish(Long userId, Long sessionId);

    /** 查询反馈报告 */
    ViewDtos.ReportVO report(Long userId, Long sessionId);

    /** 训练记录分页列表（status 为空表示全部） */
    ViewDtos.PageResult<ViewDtos.SessionVO> page(Long userId, Integer status, int page, int size);

    /** 训练记录总数 */
    int countByUser(Long userId);

    /** 进行中的会话数 */
    int countOngoing(Long userId);
}
