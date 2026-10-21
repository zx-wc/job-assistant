package com.nuist.agent.job.controller;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.zhangxu.entity.R;
import com.nuist.agent.zhangxu.auth.UserContextHolder;
import com.nuist.agent.zhangxu.dto.JobDtos;
import com.nuist.agent.zhangxu.dto.ViewDtos;
import com.nuist.agent.job.position.JobPositionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位信息管理接口（成员 B）。
 */
@RestController
@RequestMapping("/api/job/positions")
public class JobPositionController {

    private final JobPositionService positionService;

    public JobPositionController(JobPositionService positionService) {
        this.positionService = positionService;
    }

    /** 岗位分页列表 */
    @GetMapping
    public R page(@RequestParam(required = false) String keyword,
                  @RequestParam(defaultValue = "1") int page,
                  @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContextHolder.requireUserId();
        ViewDtos.PageResult<JobPosition> result = positionService.page(userId, keyword, page, size);
        return R.ok().data("page", result);
    }

    /** 新建岗位 */
    @PostMapping
    public R create(@RequestBody(required = false) JobDtos.PositionSaveRequest request) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("position", positionService.create(userId, request));
    }

    /** 岗位详情 */
    @GetMapping("/{id}")
    public R detail(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("position", positionService.detail(userId, id));
    }

    /** 编辑岗位 */
    @PutMapping("/{id}")
    public R update(@PathVariable Long id, @RequestBody(required = false) JobDtos.PositionSaveRequest request) {
        Long userId = UserContextHolder.requireUserId();
        return R.ok().data("position", positionService.update(userId, id, request));
    }

    /** 删除岗位 */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        Long userId = UserContextHolder.requireUserId();
        positionService.delete(userId, id);
        return R.ok();
    }
}
