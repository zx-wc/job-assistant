package com.nuist.agent.zhangxu.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * 大模型结构化输出解析工具（成员 A：网关结构化输出）。
 *
 * <p>大模型经常把 JSON 包在 ```json ... ``` 代码块里，或者在 JSON 前后加解释文字，
 * 因此先"清洗"再解析；解析失败统一转成 {@link LlmUnavailableException}，
 * 由业务层走本地降级。</p>
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    /**
     * 从模型输出中提取 JSON 片段（对象或数组）。
     *
     * @param raw 模型原始输出
     * @return JSON 字符串
     */
    public static String extract(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new LlmUnavailableException("AI 返回内容为空");
        }
        String text = raw.trim();
        // 1. 去掉 Markdown 代码块围栏
        int fenceStart = text.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = text.indexOf('\n', fenceStart);
            int fenceEnd = text.lastIndexOf("```");
            if (contentStart > 0 && fenceEnd > contentStart) {
                text = text.substring(contentStart + 1, fenceEnd).trim();
            }
        }
        // 2. 截取第一个 '{' / '[' 到最后一个 '}' / ']' 之间的内容
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start;
        if (objectStart < 0) {
            start = arrayStart;
        } else if (arrayStart < 0) {
            start = objectStart;
        } else {
            start = Math.min(objectStart, arrayStart);
        }
        if (start < 0) {
            throw new LlmUnavailableException("AI 返回内容中未找到 JSON");
        }
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        if (end <= start) {
            throw new LlmUnavailableException("AI 返回的 JSON 不完整");
        }
        return text.substring(start, end + 1);
    }

    /** 解析为指定类型（对象） */
    public static <T> T parse(String raw, Class<T> type, ObjectMapper mapper) {
        try {
            return mapper.readValue(extract(raw), type);
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException("AI 返回内容无法解析为 " + type.getSimpleName() + ":" + e.getMessage(), e);
        }
    }

    /** 解析为指定泛型（列表等） */
    public static <T> T parse(String raw, TypeReference<T> type, ObjectMapper mapper) {
        try {
            return mapper.readValue(extract(raw), type);
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException("AI 返回内容无法解析:" + e.getMessage(), e);
        }
    }
}
