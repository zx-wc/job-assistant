package com.nuist.agent.job.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构化输出解析测试（成员 E：单元测试）。
 * 覆盖大模型常见"不规范输出"：Markdown 代码块、前后夹带解释文字、缺少 JSON、JSON 不完整。
 */
class JsonExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extract_plainJson_shouldReturnAsIs() {
        String json = "{\"score\": 88}";
        assertEquals(json, JsonExtractor.extract(json));
    }

    @Test
    void extract_markdownFence_shouldStripFence() {
        String raw = "```json\n{\"score\": 88, \"comment\": \"不错\"}\n```";
        assertEquals("{\"score\": 88, \"comment\": \"不错\"}", JsonExtractor.extract(raw));
    }

    @Test
    void extract_withLeadingText_shouldLocateJson() {
        String raw = "好的，分析如下：\n{\"matchScore\": 72}\n希望有帮助";
        assertEquals("{\"matchScore\": 72}", JsonExtractor.extract(raw));
    }

    @Test
    void extract_jsonArray_shouldWork() {
        String raw = "结果：[{\"question\": \"Q1\"}] 以上";
        assertEquals("[{\"question\": \"Q1\"}]", JsonExtractor.extract(raw));
    }

    @Test
    void extract_emptyText_shouldThrow() {
        LlmUnavailableException error = assertThrows(LlmUnavailableException.class,
                () -> JsonExtractor.extract("   "));
        assertTrue(error.getMessage().contains("为空"));
    }

    @Test
    void extract_noJson_shouldThrow() {
        assertThrows(LlmUnavailableException.class, () -> JsonExtractor.extract("抱歉，我无法回答"));
    }

    @Test
    void extract_incompleteJson_shouldThrow() {
        assertThrows(LlmUnavailableException.class, () -> JsonExtractor.extract("{\"score\": 88"));
    }

    @Test
    void parse_object_shouldFillFields() {
        AiResults.AnswerEvalResult result = JsonExtractor.parse(
                "```json\n{\"score\": 81, \"comment\": \"结构清晰\"}\n```",
                AiResults.AnswerEvalResult.class, objectMapper);
        assertEquals(81, result.getScore());
        assertEquals("结构清晰", result.getComment());
    }

    @Test
    void parse_typeReference_shouldFillList() {
        String raw = "{\"questions\": [{\"question\": \"Q1\", \"type\": \"技能\", \"expectedPoints\": [\"A\"]}]}";
        AiResults.QuestionListResult result = JsonExtractor.parse(
                raw, new TypeReference<>() {
                }, objectMapper);
        assertEquals(1, result.getQuestions().size());
        assertEquals("Q1", result.getQuestions().get(0).getQuestion());
    }

    @Test
    void parse_unknownField_shouldBeIgnored() {
        AiResults.AnswerEvalResult result = JsonExtractor.parse(
                "{\"score\": 60, \"unknownField\": \"x\"}", AiResults.AnswerEvalResult.class, objectMapper);
        assertEquals(60, result.getScore());
    }

    @Test
    void parse_invalidJson_shouldThrowLlmUnavailable() {
        assertThrows(LlmUnavailableException.class,
                () -> JsonExtractor.parse("not a json", AiResults.AnswerEvalResult.class, objectMapper));
    }

    @Test
    void parse_dimensionScores_shouldMapChineseKeys() {
        AiResults.ResumeAnalysisResult result = JsonExtractor.parse(
                "{\"matchScore\": 70, \"dimensionScores\": {\"技能匹配\": 75, \"项目经验\": 60}}",
                AiResults.ResumeAnalysisResult.class, objectMapper);
        Map<String, Integer> dimensions = result.getDimensionScores();
        assertEquals(2, dimensions.size());
        assertEquals(75, dimensions.get("技能匹配"));
    }

    @Test
    void parse_emptyLists_shouldDefaultToEmpty() {
        AiResults.QuestionListResult result = JsonExtractor.parse("{}", AiResults.QuestionListResult.class, objectMapper);
        assertTrue(result.getQuestions().isEmpty());
    }

    @Test
    void parse_listOfStrings() throws Exception {
        List<String> list = objectMapper.readValue("[\"a\",\"b\"]", new TypeReference<List<String>>() {
        });
        assertEquals(List.of("a", "b"), list);
    }
}
