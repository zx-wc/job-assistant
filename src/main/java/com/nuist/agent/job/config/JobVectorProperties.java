package com.nuist.agent.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量检索（Milvus RAG）配置（成员 A：AI 网关能力扩展）。
 *
 * <p>求职子系统把简历正文与岗位 JD 切块向量化后写入 Milvus，分析与出题前先做语义检索，
 * 把最相关的片段作为上下文注入提示词，降低大模型"看不到原始材料"造成的泛泛而谈。</p>
 *
 * <p>Milvus 连接参数仍走 Spring AI 官方前缀 {@code spring.ai.vectorstore.milvus.*}，
 * 这里只放业务侧策略（开关、切块大小、TopK、相似度阈值、熔断窗口）。</p>
 */
@Data
@ConfigurationProperties(prefix = "job.vector")
public class JobVectorProperties {

    /** 是否启用向量检索；关闭后所有 RAG 逻辑跳过（不影响业务降级链路） */
    private boolean enabled = true;

    /** 索引集合名（与 spring.ai.vectorstore.milvus.collectionName 保持一致） */
    private String collectionName = "job_knowledge";

    /** 单个分块字符数 */
    private int chunkSize = 320;

    /** 相邻分块重叠字符数，避免切断句子 */
    private int chunkOverlap = 60;

    /** 默认检索条数 */
    private int topK = 4;

    /** 相似度阈值（COSINE，低于该值的片段丢弃） */
    private double similarityThreshold = 0.55D;

    /** 单个来源（一份简历/一个岗位）最多索引多少个分块，避免超长文本打爆向量库 */
    private int maxChunksPerSource = 24;

    /** Milvus 失败后的短路窗口（毫秒）：窗口内不再尝试，避免每次请求都等待连接超时 */
    private long unavailableWindowMs = 60_000L;
}
