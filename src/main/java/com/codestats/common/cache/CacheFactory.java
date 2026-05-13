package com.codestats.common.cache;

import com.codestats.common.cache.impl.LocalCache;

import java.lang.reflect.Constructor;

public class CacheFactory {

    /**
     * 根据配置创建缓存实例
     * @param config 配置对象
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 缓存实例
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Cache<K, V> createCache(CacheConfigBean config) {
        CacheType type = config.getType();
        long ttl = config.getTtlMillis();
        long maxSize = config.getMaxSize();
        boolean enableCleaner = config.isEnableCleaner();
        long cleanInterval = config.getCleanIntervalSeconds();

        if (type == CacheType.CAFFEINE && isCaffeinePresent()) {
            try {
                Class<?> clazz = Class.forName("com.codestats.common.cache.impl.CaffeineCache");
                Constructor<?> ctor = clazz.getConstructor(long.class, long.class);
                return (Cache<K, V>) ctor.newInstance(ttl, maxSize);
            } catch (Exception e) {
                System.err.println("Caffeine 缓存创建失败，回退到 LOCAL 缓存: " + e.getMessage());
            }
        }
        // 默认使用 LOCAL 缓存
        return new LocalCache<>(ttl, maxSize, enableCleaner, cleanInterval);
    }

    /**
     * 使用默认配置创建缓存
     */
    public static <K, V> Cache<K, V> createDefaultCache() {
        return createCache(new CacheConfigBean());
    }

    private static boolean isCaffeinePresent() {
        try {
            Class.forName("com.github.benmanes.caffeine.cache.Cache");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 缓存配置 Bean
     */
    public static class CacheConfigBean {
        private CacheType type = CacheConfig.DEFAULT_CACHE_TYPE;
        private long ttlMillis = CacheConfig.DEFAULT_TTL_MILLIS;
        private long maxSize = CacheConfig.DEFAULT_MAX_SIZE;
        private boolean enableCleaner = CacheConfig.DEFAULT_ENABLE_CLEANER;
        private long cleanIntervalSeconds = CacheConfig.DEFAULT_CLEAN_INTERVAL_SECONDS;

        // getters / setters
        public CacheType getType() { return type; }
        public void setType(CacheType type) { this.type = type; }
        public long getTtlMillis() { return ttlMillis; }
        public void setTtlMillis(long ttlMillis) { this.ttlMillis = ttlMillis; }
        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
        public boolean isEnableCleaner() { return enableCleaner; }
        public void setEnableCleaner(boolean enableCleaner) { this.enableCleaner = enableCleaner; }
        public long getCleanIntervalSeconds() { return cleanIntervalSeconds; }
        public void setCleanIntervalSeconds(long cleanIntervalSeconds) { this.cleanIntervalSeconds = cleanIntervalSeconds; }
    }
}