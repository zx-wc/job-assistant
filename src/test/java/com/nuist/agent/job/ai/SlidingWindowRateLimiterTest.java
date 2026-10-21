package com.nuist.agent.job.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 滑动窗口限流器测试（成员 E）。
 */
class SlidingWindowRateLimiterTest {

    @Test
    void withinLimit_shouldAllow() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertTrue(limiter.tryAcquire("u1", 3, 60_000));
        assertTrue(limiter.tryAcquire("u1", 3, 60_000));
        assertTrue(limiter.tryAcquire("u1", 3, 60_000));
    }

    @Test
    void exceedLimit_shouldReject() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertTrue(limiter.tryAcquire("u1", 2, 60_000));
        assertTrue(limiter.tryAcquire("u1", 2, 60_000));
        assertFalse(limiter.tryAcquire("u1", 2, 60_000));
    }

    @Test
    void differentKeys_shouldIsolate() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertTrue(limiter.tryAcquire("u1", 1, 60_000));
        assertTrue(limiter.tryAcquire("u2", 1, 60_000));
        assertFalse(limiter.tryAcquire("u1", 1, 60_000));
    }

    @Test
    void windowPassed_shouldAllowAgain() {
        AtomicLong now = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(now::get);
        assertTrue(limiter.tryAcquire("u1", 1, 1_000));
        assertFalse(limiter.tryAcquire("u1", 1, 1_000));
        now.addAndGet(1_001);
        assertTrue(limiter.tryAcquire("u1", 1, 1_000));
    }

    @Test
    void unlimitedWhenLimitNotPositive() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire("u", 0, 60_000));
        }
    }

    @Test
    void currentCount_shouldReflectWindow() {
        AtomicLong now = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(now::get);
        limiter.tryAcquire("k", 5, 1_000);
        limiter.tryAcquire("k", 5, 1_000);
        assertEquals(2, limiter.currentCount("k", 1_000));
        now.addAndGet(1_001);
        assertEquals(0, limiter.currentCount("k", 1_000));
    }

    @Test
    void cleanup_shouldRemoveEmptyKeys() {
        AtomicLong now = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(now::get);
        limiter.tryAcquire("k", 5, 100);
        now.addAndGet(200);
        limiter.cleanup(100);
        assertEquals(0, limiter.size());
    }
}
