package com.omni.common.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局缓存管理器，统一管理多个命名缓存实例
 */
public class CacheManager {

    private static final CacheManager INSTANCE = new CacheManager();
    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    private CacheManager() {}

    public static CacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取或创建指定名称的缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name) {
        return getCache(name, null);
    }

    /**
     * 获取或创建指定名称和配置的缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name, CacheFactory.CacheConfigBean config) {
        Cache<?, ?> cache = caches.get(name);
        if (cache == null) {
            synchronized (this) {
                cache = caches.get(name);
                if (cache == null) {
                    if (config == null) {
                        config = new CacheFactory.CacheConfigBean();
                    }
                    cache = CacheFactory.createCache(config);
                    caches.put(name, cache);
                }
            }
        }
        return (Cache<K, V>) cache;
    }

    /**
     * 移除指定缓存
     */
    public void removeCache(String name) {
        caches.remove(name);
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        caches.values().forEach(Cache::clear);
    }
}