package com.nuist.agent.job.common;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历敏感信息脱敏（成员 A/E：安全与合规）。
 *
 * <p>项目要求"简历内容脱敏存储，禁止记录真实敏感信息"，因此简历正文与岗位描述在入库前、
 * 送入大模型前统一脱敏：手机号、邮箱、身份证号、银行卡号只保留首尾，中间用 * 覆盖。</p>
 */
@Component
public class PrivacyMasker {

    /** 手机号：保留前 3 后 4 */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");

    /** 邮箱：保留前 3 位与域名 */
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-]{1,3})[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    /** 身份证：保留前 6 后 4 */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?!\\d)");

    /** 银行卡：16-19 位数字，保留前 4 后 4 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)(\\d{4})\\d{8,11}(\\d{4})(?!\\d)");

    /**
     * 对文本脱敏（null 安全）。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public String mask(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String masked = text;
        masked = replace(masked, ID_CARD, "$1********$2");
        masked = replace(masked, BANK_CARD, "$1********$2");
        masked = replace(masked, PHONE, "$1****$2");
        masked = replace(masked, EMAIL, "$1***$2");
        return masked;
    }

    /** 脱敏并返回命中说明（便于前端提示"已脱敏 N 处"） */
    public MaskResult maskWithReport(String text) {
        if (!StringUtils.hasText(text)) {
            return new MaskResult(text, 0);
        }
        int count = 0;
        String masked = text;
        count += countMatches(masked, ID_CARD);
        masked = replace(masked, ID_CARD, "$1********$2");
        count += countMatches(masked, BANK_CARD);
        masked = replace(masked, BANK_CARD, "$1********$2");
        count += countMatches(masked, PHONE);
        masked = replace(masked, PHONE, "$1****$2");
        count += countMatches(masked, EMAIL);
        masked = replace(masked, EMAIL, "$1***$2");
        return new MaskResult(masked, count);
    }

    private String replace(String text, Pattern pattern, String replacement) {
        return pattern.matcher(text).replaceAll(replacement);
    }

    private int countMatches(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** 脱敏结果 */
    public record MaskResult(String text, int maskedCount) {
    }
}
