package com.codestats.common.cache;

import com.codestats.common.cache.impl.LocalCache;
import com.codestats.framework.log.LoggerFactory;
import com.codestats.framework.log.core.Logger;

import java.lang.reflect.Constructor;

public class CacheFactory {
    private static final Logger log = LoggerFactory.getLogger(CacheFactory.class);

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
                log.warn("Caffeine 缓存创建失败，回退到 LOCAL 缓存: {}", e.getMessage());
            }
        }
        return new LocalCache<>(ttl, maxSize, enableCleaner, cleanInterval);
    }

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

    public static class CacheConfigBean {
        private CacheType type = CacheConfig.DEFAULT_CACHE_TYPE;
        private long ttlMillis = CacheConfig.DEFAULT_TTL_MILLIS;
        private long maxSize = CacheConfig.DEFAULT_MAX_SIZE;
        private boolean enableCleaner = CacheConfig.DEFAULT_ENABLE_CLEANER;
        private long cleanIntervalSeconds = CacheConfig.DEFAULT_CLEAN_INTERVAL_SECONDS;

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