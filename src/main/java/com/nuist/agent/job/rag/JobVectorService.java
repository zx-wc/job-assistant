package com.nuist.agent.job.rag;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;

import java.util.List;

/**
 * 求职知识库向量检索服务（成员 A：AI 基础设施 / Milvus RAG）。
 *
 * <p>把简历正文与岗位 JD 切块向量化写入 Milvus；分析与出题前按语义检索相关片段，
 * 作为上下文注入提示词。Milvus 不可用时不抛异常到业务层，而是返回空结果并记录状态，
 * 由业务走"无 RAG 上下文"的降级路径。</p>
 */
public interface JobVectorService {

    /** 配置是否启用向量检索 */
    boolean enabled();

    /** 当前是否可用（启用且未处于失败短路窗口） */
    boolean available();

    /** 向量库状态（管理端展示） */
    VectorStatus status();

    /** 建立/刷新一份简历的向量索引（先删旧分块再写入） */
    void indexResume(JobResume resume);

    /** 建立/刷新一个岗位的向量索引 */
    void indexPosition(JobPosition position);

    /** 删除某个来源的全部向量（简历/岗位删除时调用） */
    void deleteSource(String sourceType, Long sourceId);

    /**
     * 语义检索。
     *
     * @param userId     用户 id（用于数据隔离）
     * @param query      查询文本（通常是岗位要求）
     * @param topK       返回条数，<=0 时取配置默认值
     * @param sourceType resume / position；为空表示不限来源
     * @param sourceId   限定某个来源；为空表示该用户下全部来源
     * @return 命中的片段（按相似度降序），不可用时返回空列表
     */
    List<VectorHit> search(Long userId, String query, int topK, String sourceType, Long sourceId);

    /**
     * 组装注入提示词的检索上下文。
     *
     * @return 命中片段的文本块；无命中或不可用时返回 "(无)"
     */
    String buildContext(Long userId, Long resumeId, Long positionId, String query, int topK);

    /** 向量库状态 */
    record VectorStatus(boolean enabled, boolean available, String collection, int topK,
                        double similarityThreshold, long indexedChunks,
                        String lastSuccessAt, String lastError) {
    }

    /** 一条检索命中 */
    record VectorHit(String sourceType, String sourceId, String title, String text, Double score) {
    }
}
