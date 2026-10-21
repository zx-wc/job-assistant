package com.nuist.agent.zhangxu.ai;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结果缓存与限流器测试（成员 E：AI 网关组件）。
 */
class LlmResultCacheTest {

    @Test
    void putThenGet_shouldHit() {
        LlmResultCache cache = new LlmResultCache(10, 60_000);
        cache.put("k1", "v1");
        assertEquals(Optional.of("v1"), cache.get("k1"));
    }

    @Test
    void get_unknownKey_shouldMiss() {
        assertTrue(new LlmResultCache(10, 60_000).get("none").isEmpty());
    }

    @Test
    void expiredEntry_shouldMiss() {
        AtomicLong now = new AtomicLong(1_000);
        LlmResultCache cache = new LlmResultCache(10, 5_000, now::get);
        cache.put("k", "v");
        now.addAndGet(5_001);
        assertTrue(cache.get("k").isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    void ttlZero_shouldDisableCache() {
        LlmResultCache cache = new LlmResultCache(10, 0);
        cache.put("k", "v");
        assertTrue(cache.get("k").isEmpty());
    }

    @Test
    void maxEntries_shouldEvictEldest() {
        LlmResultCache cache = new LlmResultCache(2, 60_000);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        assertEquals(2, cache.size());
        assertFalse(cache.get("a").isPresent());
        assertTrue(cache.get("c").isPresent());
    }

    @Test
    void buildKey_sameInput_shouldBeStable() {
        assertEquals(LlmResultCache.buildKey("s", "abc"), LlmResultCache.buildKey("s", "abc"));
    }

    @Test
    void buildKey_differentInput_shouldDiffer() {
        assertFalse(LlmResultCache.buildKey("s", "a").equals(LlmResultCache.buildKey("s", "b")));
    }

    @Test
    void evictExpired_shouldRemoveOnlyExpired() {
        AtomicLong now = new AtomicLong(0);
        LlmResultCache cache = new LlmResultCache(10, 100, now::get);
        cache.put("old", "1");
        now.addAndGet(200);
        cache.put("new", "2");
        cache.evictExpired();
        assertEquals(1, cache.size());
        assertTrue(cache.get("new").isPresent());
    }
}
