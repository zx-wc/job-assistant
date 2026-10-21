package com.nuist.agent.job.ai;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容安全过滤（成员 A：安全与合规）。
 *
 * <p>对 AI 输出做敏感词检测：命中则用 * 脱敏并记录日志，而不是直接丢弃整段回答，
 * 既满足"AI 评价仅作练习参考"的合规要求，也不会让业务不可用。</p>
 *
 * <p>注意：项目中的 SensitiveWordBs 开启了数字检测（连续 6 位以上数字会被判定为敏感），
 * 这里过滤掉纯数字命中，避免把"2024 届""15k-25k"等正常内容误伤。</p>
 */
@Component
public class ContentSafetyFilter {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyFilter.class);

    private final SensitiveWordBs sensitiveWordBs;

    public ContentSafetyFilter(SensitiveWordBs sensitiveWordBs) {
        this.sensitiveWordBs = sensitiveWordBs;
    }

    /**
     * 对 AI 输出脱敏。
     *
     * @param text AI 原始输出
     * @return 脱敏后的文本（无命中时原样返回）
     */
    public String maskOutput(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        List<String> hits = nonNumericHits(text);
        if (hits.isEmpty()) {
            return text;
        }
        String masked = text;
        for (String word : hits) {
            if (StringUtils.hasText(word)) {
                masked = masked.replace(word, "*".repeat(word.length()));
            }
        }
        log.warn("AI 输出命中敏感词{}，已脱敏后返回", hits);
        return masked;
    }

    /** 是否包含敏感词（输入校验用，同样忽略纯数字命中） */
    public boolean containsSensitive(String text) {
        return StringUtils.hasText(text) && !nonNumericHits(text).isEmpty();
    }

    private List<String> nonNumericHits(String text) {
        List<String> result = new ArrayList<>();
        for (String word : sensitiveWordBs.findAll(text)) {
            if (StringUtils.hasText(word) && !isNumeric(word)) {
                result.add(word);
            }
        }
        return result;
    }

    private boolean isNumeric(String word) {
        return word.chars().allMatch(Character::isDigit);
    }
}
