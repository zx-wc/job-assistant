package com.nuist.agent.job.rag;

import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.entity.JobResume;
import com.nuist.agent.job.ai.AiAvailability;
import com.nuist.agent.job.config.JobVectorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Milvus 向量检索服务测试（成员 A：RAG 模块）。
 *
 * <p>用 Mockito 替身覆盖：切块与元数据、写入前删除旧分块、按用户/来源过滤、
 * 过滤表达式不被支持时的兜底检索、Milvus 故障后的短路降级、上下文拼装与状态上报。</p>
 */
class JobVectorServiceImplTest {

    private VectorStore vectorStore;
    private JobVectorProperties properties;
    private JobVectorServiceImpl service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        properties = new JobVectorProperties();
        properties.setChunkSize(100);
        properties.setChunkOverlap(20);
        properties.setTopK(3);
        properties.setMaxChunksPerSource(5);
        service = new JobVectorServiceImpl(vectorStore, properties, new AiAvailability("test-key"));
    }

    // ==================== 切块 ====================

    @Test
    void chunk_shouldSplitWithOverlapAndMetadata() {
        String text = "项目经历：负责订单模块。".repeat(20);
        List<Document> documents = service.chunk(JobVectorServiceImpl.SOURCE_RESUME, 7L, 1L, "后端简历", text);

        assertFalse(documents.isEmpty());
        assertTrue(documents.size() <= properties.getMaxChunksPerSource());
        Document first = documents.get(0);
        assertEquals("resume-7-0", first.getId());
        assertEquals("1", first.getMetadata().get("userId"));
        assertEquals("resume", first.getMetadata().get("sourceType"));
        assertEquals("7", first.getMetadata().get("sourceId"));
        assertEquals("后端简历", first.getMetadata().get("title"));
        assertTrue(first.getText().length() <= properties.getChunkSize());
    }

    @Test
    void chunk_blankText_shouldReturnEmpty() {
        assertTrue(service.chunk(JobVectorServiceImpl.SOURCE_POSITION, 1L, 1L, "岗位", "  ").isEmpty());
    }

    @Test
    void chunk_respectMaxChunks() {
        String text = "很长的简历正文。".repeat(400);
        List<Document> documents = service.chunk(JobVectorServiceImpl.SOURCE_RESUME, 1L, 1L, "标题", text);
        assertEquals(properties.getMaxChunksPerSource(), documents.size());
    }

    // ==================== 写入 ====================

    @Test
    void indexResume_shouldDeleteOldChunksThenAdd() {
        JobResume resume = new JobResume();
        resume.setId(12L);
        resume.setUserId(3L);
        resume.setTitle("后端简历");
        resume.setContent("项目经历：使用 Redis 缓存热点商品，接口 P99 从 320ms 降到 90ms。".repeat(4));

        service.indexResume(resume);

        verify(vectorStore).delete(anyString());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertFalse(captor.getValue().isEmpty());
        assertTrue(service.status().indexedChunks() > 0);
    }

    @Test
    void indexPosition_shouldWriteVectors() {
        JobPosition position = new JobPosition();
        position.setId(9L);
        position.setUserId(3L);
        position.setPosition("Java 后端");
        position.setJobDesc("负责核心系统开发");
        position.setRequirements("熟悉 Spring Boot 与 MySQL 索引优化");

        service.indexPosition(position);

        verify(vectorStore).add(any());
    }

    @Test
    void index_disabled_shouldSkipVectorStore() {
        properties.setEnabled(false);

        service.indexResume(resume(1L, 1L));

        verify(vectorStore, never()).add(any());
        assertFalse(service.available());
    }

    @Test
    void index_failure_shouldShortCircuit() {
        doThrow(new RuntimeException("milvus down")).when(vectorStore).add(any());

        service.indexResume(resume(1L, 1L));

        assertFalse(service.available());
        assertNotNull(service.status().lastError());
        // 短路期间不再访问 Milvus
        service.indexResume(resume(2L, 1L));
        verify(vectorStore, times(1)).add(any());
    }

    @Test
    void deleteSource_shouldUseMetadataExpression() {
        service.deleteSource(JobVectorServiceImpl.SOURCE_RESUME, 5L);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(vectorStore).delete(captor.capture());
        assertTrue(captor.getValue().contains("sourceType"));
        assertTrue(captor.getValue().contains("5"));
    }

    // ==================== 检索 ====================

    @Test
    void search_shouldFilterByUserAndSource() {
        List<Document> documents = new ArrayList<>();
        documents.add(document("m1", "1", "resume", "7", "命中片段"));
        documents.add(document("m2", "2", "resume", "7", "别人的片段"));
        documents.add(document("m3", "1", "position", "9", "岗位片段"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

        List<JobVectorService.VectorHit> hits = service.search(1L, "Redis 缓存", 3, "resume", 7L);

        assertEquals(1, hits.size());
        assertEquals("命中片段", hits.get(0).text());
        assertEquals("resume", hits.get(0).sourceType());
    }

    @Test
    void search_shouldPassFilterExpressionAndThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.search(1L, "查询", 2, "position", 9L);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertTrue(request.hasFilterExpression());
        assertEquals(2 * 2, request.getTopK());
        assertEquals(properties.getSimilarityThreshold(), request.getSimilarityThreshold());
    }

    @Test
    void search_filterUnsupported_shouldFallbackToPlainSearch() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("filter expression not supported"))
                .thenReturn(List.of(document("m1", "1", "position", "9", "兜底命中")));

        List<JobVectorService.VectorHit> hits = service.search(1L, "查询", 2, "position", 9L);

        assertEquals(1, hits.size());
        assertEquals("兜底命中", hits.get(0).text());
        verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
        assertTrue(service.available(), "兜底成功时不应进入短路");
    }

    @Test
    void search_milvusDown_shouldReturnEmptyAndMarkUnavailable() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertTrue(service.search(1L, "查询", 2, null, null).isEmpty());
        assertFalse(service.available());
        // 短路后直接返回，不再调用 Milvus
        assertTrue(service.search(1L, "查询2", 2, null, null).isEmpty());
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void search_blankQuery_shouldReturnEmptyWithoutCallingStore() {
        assertTrue(service.search(1L, "  ", 3, null, null).isEmpty());
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void search_deduplicateSameContent() {
        List<Document> documents = List.of(
                document("m1", "1", "resume", "7", "同一段"),
                document("m2", "1", "resume", "7", "同一段"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

        assertEquals(1, service.search(1L, "查询", 3, "resume", 7L).size());
    }

    // ==================== 上下文拼装 ====================

    @Test
    void buildContext_shouldJoinHitsWithLabels() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document("m1", "1", "position", "9", "岗位要求片段")))
                .thenReturn(List.of(document("m2", "1", "resume", "7", "简历片段")));

        String context = service.buildContext(1L, 7L, 9L, "Redis 缓存", 2);

        assertTrue(context.contains("[岗位要求]"));
        assertTrue(context.contains("[简历原文]"));
        assertTrue(context.contains("岗位要求片段"));
        assertTrue(context.contains("简历片段"));
    }

    @Test
    void buildContext_noHit_shouldReturnPlaceholder() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        assertEquals("(无)", service.buildContext(1L, 7L, 9L, "查询", 2));
    }

    @Test
    void buildContext_disabled_shouldReturnPlaceholder() {
        properties.setEnabled(false);
        assertEquals("(无)", service.buildContext(1L, 7L, 9L, "查询", 2));
    }

    // ==================== 状态 ====================

    @Test
    void status_shouldExposeConfig() {
        JobVectorService.VectorStatus status = service.status();
        assertTrue(status.enabled());
        assertTrue(status.available());
        assertEquals(properties.getCollectionName(), status.collection());
        assertEquals(properties.getTopK(), status.topK());
        assertEquals(properties.getSimilarityThreshold(), status.similarityThreshold());
    }

    // ==================== 辅助 ====================

    private Document document(String id, String userId, String sourceType, String sourceId, String text) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", userId);
        metadata.put("sourceType", sourceType);
        metadata.put("sourceId", sourceId);
        metadata.put("title", "标题");
        return Document.builder().id(id).text(text).metadata(metadata).score(0.88).build();
    }

    private JobResume resume(Long id, Long userId) {
        JobResume resume = new JobResume();
        resume.setId(id);
        resume.setUserId(userId);
        resume.setTitle("简历");
        resume.setContent("项目经历：订单模块，接口 P99 从 320ms 降到 90ms。".repeat(5));
        return resume;
    }
}
