package com.nuist.agent.zhangxu.rag;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.mapper.JobPositionMapper;
import com.nuist.agent.mapper.JobResumeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量库运维服务（成员 A：Milvus 索引重建）。
 *
 * <p>用于历史数据补索引、切换 embedding 模型后重建、以及排查向量库是否真的写入成功。</p>
 */
@Service
public class JobVectorAdminService {

    private static final Logger log = LoggerFactory.getLogger(JobVectorAdminService.class);

    private final JobVectorService vectorService;
    private final JobResumeMapper resumeMapper;
    private final JobPositionMapper positionMapper;

    public JobVectorAdminService(JobVectorService vectorService,
                                 JobResumeMapper resumeMapper,
                                 JobPositionMapper positionMapper) {
        this.vectorService = vectorService;
        this.resumeMapper = resumeMapper;
        this.positionMapper = positionMapper;
    }

    /**
     * 重建某个用户的全部简历与岗位索引。
     *
     * @return 处理结果（简历数、岗位数、是否可用）
     */
    public ReindexResult reindexUser(Long userId) {
        if (!vectorService.available()) {
            log.warn("Milvus 不可用，放弃重建索引:userId={}", userId);
            return new ReindexResult(0, 0, false, "Milvus 当前不可用");
        }
        List<JobResume> resumes = resumeMapper.selectAllByUser(userId);
        List<JobPosition> positions = positionMapper.selectAllByUser(userId);
        for (JobResume resume : resumes) {
            vectorService.indexResume(resume);
        }
        for (JobPosition position : positions) {
            vectorService.indexPosition(position);
        }
        log.info("向量索引重建完成:userId={},简历={},岗位={}", userId, resumes.size(), positions.size());
        return new ReindexResult(resumes.size(), positions.size(), true, "");
    }

    /** 重建结果 */
    public record ReindexResult(int resumes, int positions, boolean available, String message) {
    }
}
