package com.nuist.agent.job.ai;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词渲染器（成员 A：公共组件）。
 *
 * <p>只替换形如 {@code {变量名}} 的占位符（变量名限定字母、数字、下划线），
 * 因此 JSON 示例中的 {@code {"matchScore": 0}} 不会被误替换；
 * 未提供值的变量替换为空串，保证不会因为漏传变量导致调用失败。</p>
 */
public class PromptTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    /**
     * 渲染模板。
     *
     * @param template  模板内容，可为空
     * @param variables 变量表，null 表示全部替换为空串
     * @return 渲染后的文本
     */
    public String render(String template, Map<String, Object> variables) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder builder = new StringBuilder(template.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = variables == null ? null : variables.get(name);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    /** 提取模板中出现的全部占位符名称（用于模板管理页提示、单测校验） */
    public Set<String> placeholders(String template) {
        Set<String> names = new LinkedHashSet<>();
        if (!StringUtils.hasText(template)) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
