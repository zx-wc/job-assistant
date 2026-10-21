package com.nuist.agent.zhangxu.controller;

import com.nuist.agent.zhangxu.entity.LlmCallLog;
import com.nuist.agent.zhangxu.entity.R;
import com.nuist.agent.zhangxu.ai.AiAdminService;
import com.nuist.agent.zhangxu.auth.UserContextHolder;
import com.nuist.agent.zhangxu.dto.JobDtos;
import com.nuist.agent.zhangxu.dto.ViewDtos;
import com.nuist.agent.zhangxu.rag.JobVectorAdminService;
import com.nuist.agent.zhangxu.rag.JobVectorService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 网关管理接口（成员 A，仅管理员）。
 * 所有接口先做管理员权限校验（非管理员返回 403），密钥只返回脱敏值。
 * 其中 {@code /vector/**} 为 Milvus 向量库（RAG）运维接口。
 */
@RestController
@RequestMapping("/api/job/admin/ai")
public class JobAdminAiController {

    private final AiAdminService aiAdminService;
    private final JobVectorService vectorService;
    private final JobVectorAdminService vectorAdminService;

    public JobAdminAiController(AiAdminService aiAdminService,
                                JobVectorService vectorService,
                                JobVectorAdminService vectorAdminService) {
        this.aiAdminService = aiAdminService;
        this.vectorService = vectorService;
        this.vectorAdminService = vectorAdminService;
    }

    /** 网关配置（密钥脱敏） */
    @GetMapping("/config")
    public R config() {
        UserContextHolder.requireAdmin();
        return R.ok().data("config", aiAdminService.config());
    }

    /** 今日/近 7 天调用统计 */
    @GetMapping("/stats")
    public R stats() {
        UserContextHolder.requireAdmin();
        return R.ok().data("stats", aiAdminService.statistics());
    }

    /** 场景与模板来源 */
    @GetMapping("/scenes")
    public R scenes() {
        UserContextHolder.requireAdmin();
        return R.ok().data("scenes", aiAdminService.scenes());
    }

    /** 数据库模板列表 */
    @GetMapping("/templates")
    public R templates() {
        UserContextHolder.requireAdmin();
        return R.ok().data("templates", aiAdminService.templates());
    }

    /** 保存模板（覆盖数据库模板，立即生效） */
    @PutMapping("/templates/{scene}")
    public R saveTemplate(@PathVariable String scene,
                          @RequestBody(required = false) JobDtos.PromptTemplateSaveRequest request) {
        UserContextHolder.requireAdmin();
        aiAdminService.saveTemplate(scene, request);
        return R.ok();
    }

    /** 删除数据库模板，回到内置模板 */
    @DeleteMapping("/templates/{scene}")
    public R resetTemplate(@PathVariable String scene) {
        UserContextHolder.requireAdmin();
        aiAdminService.resetTemplate(scene);
        return R.ok();
    }

    /** 调用日志分页 */
    @GetMapping("/logs")
    public R logs(@RequestParam(defaultValue = "1") int page,
                  @RequestParam(defaultValue = "20") int size) {
        UserContextHolder.requireAdmin();
        ViewDtos.PageResult<LlmCallLog> result = aiAdminService.logs(page, size);
        return R.ok().data("page", result);
    }

    // ==================== Milvus 向量库（RAG）====================

    /** 向量库状态：是否启用、是否可用、集合名、已写入分块数、最近错误 */
    @GetMapping("/vector")
    public R vectorStatus() {
        UserContextHolder.requireAdmin();
        return R.ok().data("vector", vectorService.status());
    }

    /** 重建指定用户（默认当前管理员）的简历/岗位向量索引 */
    @PostMapping("/vector/reindex")
    public R vectorReindex(@RequestParam(required = false) Long userId) {
        UserContextHolder.requireAdmin();
        Long targetUserId = userId == null ? UserContextHolder.requireUserId() : userId;
        JobVectorAdminService.ReindexResult result = vectorAdminService.reindexUser(targetUserId);
        return R.ok().data("reindexed", result).data("userId", targetUserId);
    }

    /** 语义检索调试：直接看 Milvus 返回的片段（sourceType 可选 resume/position） */
    @PostMapping("/vector/search")
    public R vectorSearch(@RequestBody(required = false) Map<String, Object> body) {
        UserContextHolder.requireAdmin();
        String query = body == null ? null : String.valueOf(body.getOrDefault("query", ""));
        int topK = body == null || body.get("topK") == null
                ? 5 : Integer.parseInt(String.valueOf(body.get("topK")));
        String sourceType = body == null || body.get("sourceType") == null
                ? null : String.valueOf(body.get("sourceType"));
        List<JobVectorService.VectorHit> hits = vectorService.search(
                UserContextHolder.requireUserId(), query, topK, sourceType, null);
        return R.ok().data("hits", hits).data("count", hits.size());
    }
}
