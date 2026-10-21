package com.nuist.agent.job.rag;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.job.ai.AiAvailability;
import com.nuist.agent.job.config.JobVectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Milvus 向量检索实现（成员 A：AI 基础设施 / RAG）。
 *
 * <p>核心流程：
 * <ol>
 *   <li><b>写入</b>：简历/岗位保存后切块（chunkSize/overlap 可配）→ 先按 metadata 删除旧分块 → 写入 Milvus；</li>
 *   <li><b>检索</b>：分析/出题前用岗位要求做 query → 按 userId + sourceType + sourceId 过滤 → 取 TopK 片段；</li>
 *   <li><b>降级</b>：Milvus 连接失败或过滤表达式不被支持时，先退化为"无过滤检索 + 内存过滤"，
 *       仍失败则记录错误并短路 60 秒，业务侧拿不到上下文但流程继续（AI 正常出结果）。</li>
 * </ol>
 *
 * <p>数据隔离：所有分块的 metadata 都带 {@code userId}，检索结果在内存中再次校验 userId，
 * 杜绝跨用户召回。</p>
 */
@Service
public class JobVectorServiceImpl implements JobVectorService {

    private static final Logger log = LoggerFactory.getLogger(JobVectorServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String SOURCE_RESUME = "resume";
    public static final String SOURCE_POSITION = "position";

    private static final String META_USER_ID = "userId";
    private static final String META_SOURCE_TYPE = "sourceType";
    private static final String META_SOURCE_ID = "sourceId";
    private static final String META_TITLE = "title";
    private static final String META_CHUNK_INDEX = "chunkIndex";

    private final VectorStore vectorStore;
    private final JobVectorProperties properties;
    private final AiAvailability aiAvailability;

    /** 失败短路截止时间（毫秒） */
    private volatile long unavailableUntil = 0L;
    private volatile String lastError = "";
    private volatile String lastSuccessAt = "";
    private final AtomicLong indexedChunks = new AtomicLong();

    public JobVectorServiceImpl(@Lazy VectorStore vectorStore, JobVectorProperties properties,
                                AiAvailability aiAvailability) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.aiAvailability = aiAvailability;
    }

    @Override
    public boolean enabled() {
        // 向量化依赖 DashScope 密钥：未配置时直接禁用，避免远程鉴权失败与长时间重试
        return properties.isEnabled() && aiAvailability.configured();
    }

    @Override
    public boolean available() {
        return enabled() && System.currentTimeMillis() >= unavailableUntil;
    }

    @Override
    public VectorStatus status() {
        return new VectorStatus(enabled(), available(), properties.getCollectionName(),
                properties.getTopK(), properties.getSimilarityThreshold(), indexedChunks.get(),
                lastSuccessAt, lastError);
    }

    // ==================== 写入 ====================

    @Override
    public void indexResume(JobResume resume) {
        if (resume == null || resume.getId() == null) {
            return;
        }
        String text = joinText(resume.getTitle(), resume.getTargetPosition(), resume.getSummary(), resume.getContent());
        indexSource(SOURCE_RESUME, resume.getId(), resume.getUserId(), resume.getTitle(), text);
    }

    @Override
    public void indexPosition(JobPosition position) {
        if (position == null || position.getId() == null) {
            return;
        }
        String text = joinText(position.getPosition(), position.getCompany(), position.getCity(),
                position.getJobDesc(), position.getRequirements());
        indexSource(SOURCE_POSITION, position.getId(), position.getUserId(), position.getPosition(), text);
    }

    /** 切块 + 写入 Milvus */
    private void indexSource(String sourceType, Long sourceId, Long userId, String title, String text) {
        if (!enabled()) {
            log.debug("向量检索已关闭，跳过索引:{}#{}", sourceType, sourceId);
            return;
        }
        if (!available()) {
            log.info("Milvus 处于不可用短路窗口，跳过索引:{}#{}", sourceType, sourceId);
            return;
        }
        List<Document> documents = chunk(sourceType, sourceId, userId, title, text);
        if (documents.isEmpty()) {
            return;
        }
        // 先删旧分块：连接类错误直接短路（避免再等一次超时）；过滤表达式不支持则继续写入
        try {
            vectorStore.delete(sourceExpression(sourceType, sourceId));
        } catch (Exception deleteError) {
            if (isConnectionError(deleteError)) {
                markUnavailable("写入向量", deleteError);
                return;
            }
            log.warn("删除旧向量失败（继续写入新分块）:{}#,原因={}", sourceType, sourceId, rootMessage(deleteError));
        }
        try {
            vectorStore.add(documents);
            indexedChunks.addAndGet(documents.size());
            markSuccess();
            log.info("Milvus 索引完成:{}#{},分块={}", sourceType, sourceId, documents.size());
        } catch (Exception e) {
            markUnavailable("写入向量", e);
        }
    }

    /** 切块：固定窗口 + 重叠，单来源最多 maxChunksPerSource 块 */
    List<Document> chunk(String sourceType, Long sourceId, Long userId, String title, String text) {
        List<Document> documents = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return documents;
        }
        int size = Math.max(80, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), size / 2));
        int step = Math.max(1, size - overlap);
        int maxChunks = Math.max(1, properties.getMaxChunksPerSource());
        int index = 0;
        for (int start = 0; start < text.length() && documents.size() < maxChunks; start += step) {
            int end = Math.min(text.length(), start + size);
            String content = text.substring(start, end).trim();
            if (content.length() >= 20) {
                Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                metadata.put(META_USER_ID, String.valueOf(userId));
                metadata.put(META_SOURCE_TYPE, sourceType);
                metadata.put(META_SOURCE_ID, String.valueOf(sourceId));
                metadata.put(META_TITLE, title == null ? "" : title);
                metadata.put(META_CHUNK_INDEX, index);
                documents.add(Document.builder()
                        .id(sourceType + "-" + sourceId + "-" + index)
                        .text(content)
                        .metadata(metadata)
                        .build());
                index++;
            }
            if (end >= text.length()) {
                break;
            }
        }
        return documents;
    }

    @Override
    public void deleteSource(String sourceType, Long sourceId) {
        if (!enabled()) {
            return;
        }
        deleteSourceQuietly(sourceType, sourceId);
    }

    /** 删除某来源旧分块；失败只记录，不阻断后续写入 */
    private void deleteSourceQuietly(String sourceType, Long sourceId) {
        try {
            vectorStore.delete(sourceExpression(sourceType, sourceId));
        } catch (Exception e) {
            log.warn("删除旧向量失败（不影响新分块写入）:{}#,原因={}", sourceType, sourceId, rootMessage(e));
        }
    }

    // ==================== 检索 ====================

    @Override
    public List<VectorHit> search(Long userId, String query, int topK, String sourceType, Long sourceId) {
        if (!enabled() || !available() || !StringUtils.hasText(query)) {
            return List.of();
        }
        int limit = topK <= 0 ? properties.getTopK() : topK;
        try {
            List<Document> documents = similaritySearch(query, limit, userId, sourceType, sourceId);
            List<VectorHit> hits = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Document document : documents) {
                Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
                if (!matches(metadata, userId, sourceType, sourceId)) {
                    continue;
                }
                String key = text(metadata.get(META_SOURCE_TYPE)) + "#" + text(metadata.get(META_SOURCE_ID))
                        + "#" + document.getText();
                if (!seen.add(key)) {
                    continue;
                }
                hits.add(new VectorHit(text(metadata.get(META_SOURCE_TYPE)), text(metadata.get(META_SOURCE_ID)),
                        text(metadata.get(META_TITLE)), document.getText(), document.getScore()));
                if (hits.size() >= limit) {
                    break;
                }
            }
            markSuccess();
            return hits;
        } catch (Exception e) {
            markUnavailable("向量检索", e);
            return List.of();
        }
    }

    /** 先按 metadata 过滤检索；过滤表达式不被支持时退化为无过滤检索（结果再内存过滤） */
    private List<Document> similaritySearch(String query, int limit, Long userId, String sourceType, Long sourceId) {
        try {
            SearchRequest filtered = SearchRequest.builder()
                    .query(query)
                    .topK(limit * 2)
                    .similarityThreshold(properties.getSimilarityThreshold())
                    .filterExpression(buildExpression(userId, sourceType, sourceId))
                    .build();
            return safeList(vectorStore.similaritySearch(filtered));
        } catch (Exception filterError) {
            String message = rootMessage(filterError).toLowerCase(java.util.Locale.ROOT);
            boolean filterUnsupported = message.contains("filter") || message.contains("expression")
                    || message.contains("not support") || message.contains("unsupported")
                    || message.contains("json");
            if (!filterUnsupported) {
                // 连接类错误直接抛出，由上层记录短路（避免再等一次超时）
                if (filterError instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(filterError);
            }
            log.warn("带过滤条件的向量检索失败，退化为无过滤检索:{}", message);
            SearchRequest plain = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(limit * 4, 8))
                    .similarityThreshold(properties.getSimilarityThreshold())
                    .build();
            return safeList(vectorStore.similaritySearch(plain));
        }
    }

    private String buildExpression(Long userId, String sourceType, Long sourceId) {
        StringBuilder expression = new StringBuilder();
        if (userId != null) {
            expression.append(META_USER_ID).append(" == '").append(userId).append('\'');
        }
        if (StringUtils.hasText(sourceType)) {
            if (expression.length() > 0) {
                expression.append(" && ");
            }
            expression.append(META_SOURCE_TYPE).append(" == '").append(sourceType).append('\'');
        }
        if (sourceId != null) {
            if (expression.length() > 0) {
                expression.append(" && ");
            }
            expression.append(META_SOURCE_ID).append(" == '").append(sourceId).append('\'');
        }
        return expression.length() == 0 ? null : expression.toString();
    }

    private String sourceExpression(String sourceType, Long sourceId) {
        return META_SOURCE_TYPE + " == '" + sourceType + "' && " + META_SOURCE_ID + " == '" + sourceId + "'";
    }

    private boolean matches(Map<String, Object> metadata, Long userId, String sourceType, Long sourceId) {
        if (userId != null && !String.valueOf(userId).equals(text(metadata.get(META_USER_ID)))) {
            return false;
        }
        if (StringUtils.hasText(sourceType)
                && !sourceType.equals(text(metadata.get(META_SOURCE_TYPE)))) {
            return false;
        }
        return sourceId == null || String.valueOf(sourceId).equals(text(metadata.get(META_SOURCE_ID)));
    }

    @Override
    public String buildContext(Long userId, Long resumeId, Long positionId, String query, int topK) {
        if (!enabled() || !available() || !StringUtils.hasText(query)) {
            return "(无)";
        }
        List<VectorHit> hits = new ArrayList<>();
        hits.addAll(search(userId, query, topK, SOURCE_POSITION, positionId));
        hits.addAll(search(userId, query, topK, SOURCE_RESUME, resumeId));
        if (hits.isEmpty()) {
            return "(无)";
        }
        StringBuilder context = new StringBuilder();
        for (VectorHit hit : hits) {
            String label = SOURCE_POSITION.equals(hit.sourceType()) ? "岗位要求" : "简历原文";
            context.append("- [").append(label).append("] ")
                    .append(hit.text().replaceAll("\\s+", " ").trim())
                    .append('\n');
        }
        log.info("RAG 检索完成:userId={},命中={},query长度={}", userId, hits.size(), query.length());
        return context.toString().trim();
    }

    // ==================== 内部工具 ====================

    private void markSuccess() {
        lastSuccessAt = LocalDateTime.now().format(TIME_FORMAT);
        lastError = "";
    }

    /** 判断是否为连接类错误（Milvus 未启动 / 网络不通 / 超时） */
    private boolean isConnectionError(Throwable error) {
        String message = rootMessage(error).toLowerCase(java.util.Locale.ROOT);
        return message.contains("connect") || message.contains("deadline") || message.contains("timeout")
                || message.contains("refused") || message.contains("unavailable")
                || message.contains("initialize") || message.contains("resolve");
    }

    private void markUnavailable(String action, Exception e) {
        unavailableUntil = System.currentTimeMillis() + Math.max(1_000L, properties.getUnavailableWindowMs());
        lastError = truncate(action + "失败:" + rootMessage(e), 200);
        log.warn("Milvus {}失败，{}ms 内跳过向量操作:{}", action,
                properties.getUnavailableWindowMs(), lastError);
    }

    private List<Document> safeList(List<Document> documents) {
        return documents == null ? List.of() : documents;
    }

    private String joinText(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                builder.append(part.trim()).append('\n');
            }
        }
        return builder.toString();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private String rootMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.hasText(message) ? message : current.getClass().getSimpleName();
    }
}
