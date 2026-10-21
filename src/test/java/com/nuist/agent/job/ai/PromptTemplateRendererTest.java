package com.nuist.agent.job.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词渲染测试（成员 E）。
 * 重点：只替换 {变量}，不破坏 JSON 示例中的大括号。
 */
class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    @Test
    void render_shouldReplaceVariables() {
        String template = "岗位：{positionTitle}，公司：{company}";
        String result = renderer.render(template, Map.of("positionTitle", "Java 后端", "company", "某公司"));
        assertEquals("岗位：Java 后端，公司：某公司", result);
    }

    @Test
    void render_missingVariable_shouldBecomeEmpty() {
        String result = renderer.render("岗位：{positionTitle}，公司：{company}", Map.of("positionTitle", "Java"));
        assertEquals("岗位：Java，公司：", result);
    }

    @Test
    void render_nullVariables_shouldNotThrow() {
        assertEquals("岗位：", renderer.render("岗位：{title}", null));
    }

    @Test
    void render_jsonExample_shouldNotBeTouched() {
        String template = "输出：{\"matchScore\": 0, \"summary\": \"\"} 岗位 {positionTitle}";
        String result = renderer.render(template, Map.of("positionTitle", "后端"));
        assertEquals("输出：{\"matchScore\": 0, \"summary\": \"\"} 岗位 后端", result);
    }

    @Test
    void render_blankTemplate_shouldReturnEmpty() {
        assertEquals("", renderer.render(null, Map.of()));
        assertEquals("", renderer.render("  ", Map.of()));
    }

    @Test
    void render_replacementWithDollarSign_shouldBeLiteral() {
        String result = renderer.render("价格 {price}", Map.of("price", "$100"));
        assertEquals("价格 $100", result);
    }

    @Test
    void placeholders_shouldExtractNames() {
        Set<String> names = renderer.placeholders("A {x} B {y} C {x}");
        assertEquals(Set.of("x", "y"), names);
    }

    @Test
    void placeholders_blankTemplate_shouldBeEmpty() {
        assertTrue(renderer.placeholders(null).isEmpty());
        assertFalse(renderer.placeholders("{a}").isEmpty());
    }
}
