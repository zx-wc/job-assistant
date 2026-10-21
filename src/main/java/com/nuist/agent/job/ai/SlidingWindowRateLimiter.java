package com.nuist.agent.job.ai;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;

/**
 * 滑动窗口限流器（成员 A：AI 网关限流）。
 *
 * <p>按 key（通常是 userId）在时间窗口内限制调用次数，超过阈值返回 false。
 * 实现轻量、无第三方依赖，单机内存足够课程项目使用；分布式部署可替换为 Redis 实现。</p>
 */
public class SlidingWindowRateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public SlidingWindowRateLimiter() {
        this(System::currentTimeMillis);
    }

    /** 便于单元测试注入可控时钟 */
    public SlidingWindowRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * 尝试获取一次调用额度。
     *
     * @param key          限流维度 key（如 user:1）
     * @param limit        窗口内最大次数，&lt;=0 表示不限流
     * @param windowMillis 窗口长度（毫秒）
     * @return true 允许 / false 被限流
     */
    public boolean tryAcquire(String key, int limit, long windowMillis) {
        if (limit <= 0 || windowMillis <= 0) {
            return true;
        }
        long now = clock.getAsLong();
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() >= windowMillis) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    /** 当前窗口内的调用次数 */
    public int currentCount(String key, long windowMillis) {
        Deque<Long> queue = hits.get(key);
        if (queue == null) {
            return 0;
        }
        long now = clock.getAsLong();
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() >= windowMillis) {
                queue.pollFirst();
            }
            return queue.size();
        }
    }

    /** 重置某个 key（单测/后台运维用） */
    public void reset(String key) {
        hits.remove(key);
    }

    /** 清理空窗口，避免长期运行内存膨胀 */
    public void cleanup(long windowMillis) {
        long now = clock.getAsLong();
        hits.entrySet().removeIf(entry -> {
            Deque<Long> queue = entry.getValue();
            synchronized (queue) {
                while (!queue.isEmpty() && now - queue.peekFirst() >= windowMillis) {
                    queue.pollFirst();
                }
                return queue.isEmpty();
            }
        });
    }

    /** 当前 key 数量（单测断言用） */
    public int size() {
        return hits.size();
    }
}
