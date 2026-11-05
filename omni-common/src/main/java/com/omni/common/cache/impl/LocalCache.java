package com.omni.common.cache.impl;

import com.omni.common.cache.Cache;
import com.omni.common.cache.CacheConfig;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

public class LocalCache<K, V> implements Cache<K, V> {

    private final Map<K, CacheEntry<V>> cache;
    private final long defaultTtlMillis;
    private final ScheduledExecutorService cleaner;
    private final long maxSize;

    private static class CacheEntry<V> {
        final V value;
        final long expireTime;

        CacheEntry(V value, long ttlMillis) {
            this.value = value;
            this.expireTime = (ttlMillis <= 0) ? -1 : System.currentTimeMillis() + ttlMillis;
        }
        boolean isExpired() {
            return expireTime != -1 && System.currentTimeMillis() > expireTime;
        }
    }

    public LocalCache() {
        this(CacheConfig.DEFAULT_TTL_MILLIS, CacheConfig.DEFAULT_MAX_SIZE,
                CacheConfig.DEFAULT_ENABLE_CLEANER, CacheConfig.DEFAULT_CLEAN_INTERVAL_SECONDS);
    }

    public LocalCache(long defaultTtlMillis, long maxSize,
                      boolean enableCleaner, long cleanIntervalSeconds) {
        this.defaultTtlMillis = defaultTtlMillis;
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>();
        if (enableCleaner) {
            // ★ 修复：清理线程设为守护线程，避免残留非守护线程阻塞 JVM 退出
            this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cache-cleaner");
                t.setDaemon(true);
                return t;
            });
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
        if (maxSize > 0 && cache.size() >= maxSize) evictOneEntry();
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
        CacheEntry<V> newEntry = new CacheEntry<>(loader.apply(key), defaultTtlMillis);
        CacheEntry<V> existing = cache.putIfAbsent(key, newEntry);
        if (existing == null) return newEntry.value;
        if (existing.isExpired()) {
            cache.replace(key, newEntry);
            return newEntry.value;
        }
        return existing.value;
    }

    @Override public void remove(K key) { cache.remove(key); }
    @Override public void clear() { cache.clear(); }
    @Override public long size() { return cache.size(); }
    @Override public boolean containsKey(K key) { return get(key) != null; }

    private void evictOneEntry() {
        for (K key : cache.keySet()) {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && entry.isExpired()) {
                cache.remove(key);
                return;
            }
        }
        if (!cache.isEmpty()) {
            K firstKey = cache.keySet().iterator().next();
            cache.remove(firstKey);
        }
    }

    private void evictExpiredEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public void destroy() {
        if (cleaner != null && !cleaner.isShutdown()) {
            cleaner.shutdownNow();
        }
        cache.clear();
    }
}