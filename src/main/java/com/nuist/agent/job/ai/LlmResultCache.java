package com.nuist.agent.job.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * AI 结果缓存（成员 A：网关结果缓存）。
 *
 * <p>相同场景 + 相同输入的请求在 TTL 内直接复用上次结果，用于降低费用与响应时间
 * （例如反复分析同一份简历与同一个岗位）。实现为带 TTL 的 LRU 内存缓存。</p>
 */
public class LlmResultCache {

    private final int maxEntries;
    private final long ttlMillis;
    private final LongSupplier clock;
    /**
     * 注意：值类型命名为 CacheEntry，而不是 Entry。
     * 因为匿名内部类继承自 LinkedHashMap，类内 Map.Entry 会遮蔽外部同名嵌套类型，
     * 若把内部类命名为 Entry，removeEldestEntry(Map.Entry&lt;String, Entry&gt;) 会被解析成
     * Map.Entry&lt;String, Map.Entry&gt;，参数列表与原方法不一致，javac 报"名称冲突"错误。
     */
    private final LinkedHashMap<String, CacheEntry> store;

    public LlmResultCache(int maxEntries, long ttlMillis) {
        this(maxEntries, ttlMillis, System::currentTimeMillis);
    }

    /** 便于单元测试注入可控时钟 */
    public LlmResultCache(int maxEntries, long ttlMillis, LongSupplier clock) {
        this.maxEntries = Math.max(1, maxEntries);
        this.ttlMillis = ttlMillis;
        this.clock = clock;
        this.store = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, CacheEntry> eldest) {
                return size() > LlmResultCache.this.maxEntries;
            }
        };
    }

    /** 读取缓存，未命中或已过期返回 empty */
    public synchronized Optional<String> get(String key) {
        if (ttlMillis <= 0 || key == null) {
            return Optional.empty();
        }
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.getAsLong() - entry.createdAt >= ttlMillis) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    /** 写入缓存（ttl &lt;= 0 时不缓存） */
    public synchronized void put(String key, String value) {
        if (ttlMillis <= 0 || key == null || value == null) {
            return;
        }
        store.put(key, new CacheEntry(value, clock.getAsLong()));
    }

    public synchronized void clear() {
        store.clear();
    }

    public synchronized int size() {
        return store.size();
    }

    /** 清理过期条目（后台/单测可调用） */
    public synchronized void evictExpired() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<String, CacheEntry>> iterator = store.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().createdAt >= ttlMillis) {
                iterator.remove();
            }
        }
    }

    /**
     * 生成缓存键：场景 + 输入内容的 SHA-256，避免把超长提示词直接当 key。
     */
    public static String buildKey(String scene, String content) {
        return scene + ":" + sha256(content);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然支持 SHA-256，兜底用 hashCode 保证可用性
            return Integer.toHexString((text == null ? "" : text).hashCode());
        }
    }

    /** 缓存条目（值类型）。命名避开 Entry，避免与 java.util.Map.Entry 在匿名内部类中冲突 */
    private record CacheEntry(String value, long createdAt) {
    }
}
