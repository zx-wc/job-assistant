package com.nuist.agent.job.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历敏感信息脱敏测试（成员 E：安全合规）。
 */
class PrivacyMaskerTest {

    private final PrivacyMasker masker = new PrivacyMasker();

    @Test
    void mask_phone_shouldKeepHeadAndTail() {
        String masked = masker.mask("联系电话：13812345678");
        assertTrue(masked.contains("138****5678"));
        assertFalse(masked.contains("13812345678"));
    }

    @Test
    void mask_email_shouldHideLocalPart() {
        String masked = masker.mask("邮箱 zhang.san@example.com");
        assertTrue(masked.contains("zha***@example.com"));
    }

    @Test
    void mask_idCard_shouldHideMiddle() {
        String masked = masker.mask("身份证 320102199001011234");
        assertTrue(masked.contains("320102********1234"));
    }

    @Test
    void mask_bankCard_shouldHideMiddle() {
        String masked = masker.mask("卡号 6222021234567890123");
        assertFalse(masked.contains("6222021234567890123"));
        assertTrue(masked.contains("6222"));
    }

    @Test
    void mask_normalText_shouldBeUnchanged() {
        String text = "Java 后端开发，熟悉 Spring Boot 与 MySQL，接口 P99 从 320ms 降到 90ms";
        assertEquals(text, masker.mask(text));
    }

    @Test
    void mask_shortNumbers_shouldNotBeMasked() {
        String text = "2024 届毕业生，GPA 3.6，排名前 10%";
        assertEquals(text, masker.mask(text));
    }

    @Test
    void mask_nullOrBlank_shouldReturnAsIs() {
        assertNull(masker.mask(null));
        assertEquals("", masker.mask(""));
    }

    @Test
    void maskWithReport_shouldCountHits() {
        PrivacyMasker.MaskResult result = masker.maskWithReport(
                "电话 13812345678，邮箱 abc@test.com，备用 13900001111");
        assertEquals(3, result.maskedCount());
        assertFalse(result.text().contains("13812345678"));
    }

    @Test
    void maskWithReport_noHit_shouldCountZero() {
        assertEquals(0, masker.maskWithReport("只有正常内容").maskedCount());
    }
}
