package com.nuist.agent.job.controller;

import com.nuist.agent.zhangxu.common.ErrorCode;
import com.nuist.agent.zhangxu.entity.R;
import com.nuist.agent.zhangxu.auth.UserContextHolder;
import com.nuist.agent.zhangxu.dto.JobDtos;
import com.nuist.agent.job.interview.InterviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文字模拟面试接口（成员 C）。
 */
@RestController
@RequestMapping("/api/job/interviews")
public class JobInterviewController {

    private final InterviewService interviewService;

    public JobInterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    /** 开始面试：{positionId, resumeId?, title?, difficulty?, questionCount?} */
    @PostMapping
    public R start(@RequestBody(required = false) JobDtos.InterviewStartRequest request) {
        Long userId = UserContextHolder.requireUserId();
        if (request == null || request.getPositionId() == null) {
            return R.error(ErrorCode.PARAMETER_ERROR.getCode(), "positionId 不能为空");
        }
        return R.ok().data("session", interviewService.start(userId, request));
    }

    /** 训练记录分页列表（status 0 进行中 / 1 已结束，不传查全部） */
    @GetMapping
    public R page(@RequestParam(required = false) Integer status,
                  @RequestParam(defaultValue = "1") int page,
                  @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("page", interviewService.page(userId, status, page, size));
    }

    /** 会话详情（含题目、作答、报告） */
    @GetMapping("/{id}")
    public R detail(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("session", interviewService.detail(userId, id));
    }

    /** 提交单题作答：{questionNo, answer} */
    @PostMapping("/{id}/answers")
    public R answer(@PathVariable Long id, @RequestBody(required = false) JobDtos.InterviewAnswerRequest request) {
        Long userId = UserContextHolder.requireUserId();
        if (request == null) {
            return R.error(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        return R.ok().data("result", interviewService.answer(userId, id, request.getQuestionNo(), request.getAnswer()));
    }

    /** 多轮追问：{questionNo, extra?} */
    @PostMapping("/{id}/follow-up")
    public R followUp(@PathVariable Long id, @RequestBody(required = false) JobDtos.FollowUpRequest request) {
        Long userId = UserContextHolder.requireUserId();
        if (request == null || request.getQuestionNo() == null) {
            return R.error(ErrorCode.PARAMETER_ERROR.getCode(), "questionNo 不能为空");
        }
        return R.ok().data("session", interviewService.followUp(userId, id, request.getQuestionNo(), request.getExtra()));
    }

    /** 结束面试并生成反馈报告 */
    @PostMapping("/{id}/finish")
    public R finish(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("report", interviewService.finish(userId, id));
    }

    /** 查询反馈报告 */
    @GetMapping("/{id}/report")
    public R report(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("report", interviewService.report(userId, id));
    }
}
