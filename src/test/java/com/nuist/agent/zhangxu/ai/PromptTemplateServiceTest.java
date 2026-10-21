package com.nuist.agent.zhangxu.ai;

import com.nuist.agent.zhangxu.entity.LlmPromptTemplate;
import com.nuist.agent.zhangxu.mapper.LlmPromptTemplateMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 提示词模板服务测试（成员 E）。
 * 覆盖：数据库模板优先、内置模板回退、DB 异常兜底、模板缓存与失效、占位符提取。
 */
class PromptTemplateServiceTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    @Test
    void template_dbConfigured_shouldUseDatabase() {
        LlmPromptTemplateMapper mapper = mock(LlmPromptTemplateMapper.class);
        LlmPromptTemplate template = new LlmPromptTemplate();
        template.setScene("resume-analysis");
        template.setContent("数据库模板：{resume}");
        template.setEnabled(1);
        when(mapper.selectEnabledByScene("resume-analysis")).thenReturn(template);

        PromptTemplateService service = new PromptTemplateService(mapper, renderer, new DefaultResourceLoader());
        PromptTemplateService.TemplateInfo info = service.template(PromptScene.RESUME_ANALYSIS);

        assertEquals("DB", info.source());
        assertEquals("数据库模板：简历正文", service.resolve(PromptScene.RESUME_ANALYSIS, Map.of("resume", "简历正文")));
    }

    @Test
    void template_noDbRecord_shouldFallbackToBuiltin() {
        LlmPromptTemplateMapper mapper = mock(LlmPromptTemplateMapper.class);
        when(mapper.selectEnabledByScene("resume-analysis")).thenReturn(null);

        PromptTemplateService service = new PromptTemplateService(mapper, renderer, new DefaultResourceLoader());
        PromptTemplateService.TemplateInfo info = service.template(PromptScene.RESUME_ANALYSIS);

        assertEquals("BUILTIN", info.source());
        assertTrue(info.content().contains("matchScore"), "内置模板应包含 JSON 输出契约");
    }

    @Test
    void template_dbThrows_shouldFallbackToBuiltin() {
        LlmPromptTemplateMapper mapper = mock(LlmPromptTemplateMapper.class);
        when(mapper.selectEnabledByScene("interview-question")).thenThrow(new IllegalStateException("DB down"));

        PromptTemplateService service = new PromptTemplateService(mapper, renderer, new DefaultResourceLoader());
        assertEquals("BUILTIN", service.template(PromptScene.INTERVIEW_QUESTION).source());
    }

    @Test
    void allScenes_shouldHaveBuiltinTemplate() {
        LlmPromptTemplateMapper mapper = mock(LlmPromptTemplateMapper.class);
        PromptTemplateService service = new PromptTemplateService(mapper, renderer, new DefaultResourceLoader());
        for (PromptScene scene : PromptScene.values()) {
            String content = service.readBuiltin(scene);
            assertFalse(content.isBlank(), scene.getCode() + " 缺少内置模板");
        }
    }

    @Test
    void resolve_shouldRenderVariables() {
        LlmPromptTemplateMapper mapper = mock(LlmPromptTemplateMapper.class);
        PromptTemplateService service = new PromptTemplateService(mapper, renderer, new DefaultResourceLoader());
        String prompt = service.resolve(PromptScene.INTERVIEW_QUESTION, Map.of(
                "positionTitle", "Java 后端",
                "company", "某公司",
                "jobDesc", "负责后端开发",
                "requirements", "熟悉 Spring Boot",
                "resume", "项目经历…",
                "questionCount", 5,
                "difficulty", "NORMAL"));
        assertTrue(prompt.contains("Java 后端"));
        assertTrue(prompt.contains("某公司"));
        assertTrue(prompt.contains("5"));
        assertFalse(prompt.contains("{positionTitle}"));
    }

    @Test
    void evictAll_shouldReloadTemplate() {
        LlmPromptTemplateMapper mapper = mock(LlmPromptTemplateMapper.class);
        LlmPromptTemplate first = new LlmPromptTemplate();
        first.setContent("第一版");
        first.setEnabled(1);
        LlmPromptTemplate second = new LlmPromptTemplate();
        second.setContent("第二版");
        second.setEnabled(1);
        when(mapper.selectEnabledByScene("resume-analysis")).thenReturn(first, second);

        PromptTemplateService service = new PromptTemplateService(mapper, renderer, new DefaultResourceLoader());
        assertEquals("第一版", service.template(PromptScene.RESUME_ANALYSIS).content());
        service.evictAll();
        assertEquals("第二版", service.template(PromptScene.RESUME_ANALYSIS).content());
    }

    @Test
    void placeholders_shouldComeFromTemplate() {
        LlmPromptTemplateMapper mapper = mock(LlmPromptTemplateMapper.class);
        PromptTemplateService service = new PromptTemplateService(mapper, renderer, new DefaultResourceLoader());
        assertTrue(service.placeholders(PromptScene.INTERVIEW_REPORT).contains("qa"));
    }

    @Test
    void template_resourceMissing_shouldUseFallbackText() {
        LlmPromptTemplateMapper mapper = mock(LlmPromptTemplateMapper.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource missing = mock(Resource.class);
        when(missing.exists()).thenReturn(false);
        when(resourceLoader.getResource(org.mockito.ArgumentMatchers.anyString())).thenReturn(missing);

        PromptTemplateService service = new PromptTemplateService(mapper, renderer, resourceLoader);
        PromptTemplateService.TemplateInfo info = service.template(PromptScene.INTERVIEW_FOLLOW_UP);
        assertEquals("FALLBACK", info.source());
        assertTrue(info.content().contains("求职辅导助手"));
    }

    @Test
    void promptScene_of_shouldResolveCode() {
        assertEquals(PromptScene.RESUME_ANALYSIS, PromptScene.of("resume-analysis"));
        assertEquals(PromptScene.INTERVIEW_QUESTION, PromptScene.of("INTERVIEW-QUESTION"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PromptScene.of("unknown"));
    }
}
