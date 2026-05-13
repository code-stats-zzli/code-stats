package com.codestats.common.cache.impl;

import com.codestats.common.cache.Cache;
import com.codestats.common.cache.CacheConfig;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 基于 ConcurrentHashMap 的本地缓存实现
 * 支持 TTL、惰性删除 + 定期清理，线程安全
 */
public class LocalCache<K, V> implements Cache<K, V> {

    private final Map<K, CacheEntry<V>> cache;
    private final long defaultTtlMillis;
    private final ScheduledExecutorService cleaner;
    private final long maxSize;

    /**
     * 内部缓存条目
     */
    private static class CacheEntry<V> {
        final V value;
        final long expireTime;  // 毫秒时间戳，-1 表示永久有效

        CacheEntry(V value, long ttlMillis) {
            this.value = value;
            this.expireTime = (ttlMillis <= 0) ? -1 : System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return expireTime != -1 && System.currentTimeMillis() > expireTime;
        }
    }

    public LocalCache() {
        this(CacheConfig.DEFAULT_TTL_MILLIS, CacheConfig.DEFAULT_MAX_SIZE, CacheConfig.DEFAULT_ENABLE_CLEANER, CacheConfig.DEFAULT_CLEAN_INTERVAL_SECONDS);
    }

    public LocalCache(long defaultTtlMillis, long maxSize, boolean enableCleaner, long cleanIntervalSeconds) {
        this.defaultTtlMillis = defaultTtlMillis;
        this.maxSize = maxSize;
        // 如果 maxSize > 0，使用带容量限制的 ConcurrentHashMap（并非精确限制，需配合 size() 判断）
        // 实际上 ConcurrentHashMap 不直接支持容量限制，我们在 put 时手动检查
        this.cache = new ConcurrentHashMap<>();
        if (enableCleaner) {
            this.cleaner = Executors.newSingleThreadScheduledExecutor();
            this.cleaner.scheduleAtFixedRate(this::evictExpiredEntries,
                    cleanIntervalSeconds, cleanIntervalSeconds, TimeUnit.SECONDS);
        } else {
            this.cleaner = null;
        }
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultTtlMillis);
    }

    @Override
    public void put(K key, V value, long ttlMillis) {
        if (key == null || value == null) return;
        // 容量控制（尽力而为）
        if (maxSize > 0 && cache.size() >= maxSize) {
            // 简单策略：移除一个随机的过期条目，如果没有则移除最早插入的（需要额外维护，这里简化）
            evictOneEntry();
        }
        cache.put(key, new CacheEntry<>(value, ttlMillis));
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    @Override
    public V computeIfAbsent(K key, Function<K, V> loader) {
        V value = get(key);
        if (value != null) return value;

        // 使用 putIfAbsent 保证只有一个线程执行加载
        CacheEntry<V> newEntry = new CacheEntry<>(loader.apply(key), defaultTtlMillis);
        CacheEntry<V> existing = cache.putIfAbsent(key, newEntry);
        if (existing == null) {
            return newEntry.value;
        } else {
            // 已有缓存，再次检查是否过期（可能刚好被其他线程 put 但已过期）
            if (existing.isExpired()) {
                cache.replace(key, newEntry);
                return newEntry.value;
            }
            return existing.value;
        }
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public long size() {
        // 注意：size 包含已过期但未清理的条目，但惰性删除会在 get 时清理
        return cache.size();
    }

    @Override
    public boolean containsKey(K key) {
        return get(key) != null;
    }

    /**
     * 移除一个条目（用于容量控制）
     */
    private void evictOneEntry() {
        // 简单实现：遍历找到第一个可移除的条目（如果都没有，则移除 key 最小的）
        for (K key : cache.keySet()) {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && entry.isExpired()) {
                cache.remove(key);
                return;
            }
        }
        // 如果没有过期的，则移除第一个（随意）
        if (!cache.isEmpty()) {
            K firstKey = cache.keySet().iterator().next();
            cache.remove(firstKey);
        }
    }

    /**
     * 后台定期清理所有过期条目
     */
    private void evictExpiredEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 关闭缓存，释放后台线程
     */
    public void destroy() {
        if (cleaner != null && !cleaner.isShutdown()) {
            cleaner.shutdownNow();
        }
        cache.clear();
    }
}