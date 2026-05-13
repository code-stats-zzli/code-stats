package com.run;

import com.codestats.common.cache.Cache;
import com.codestats.common.cache.CacheFactory;
import com.codestats.common.cache.CacheManager;
import com.codestats.common.cache.CacheType;

public class CacheDemo {
    public static void main(String[] args) {
        // 1. 使用默认配置创建缓存
        Cache<String, String> cache = CacheFactory.createDefaultCache();
        cache.put("key1", "value1");
        System.out.println(cache.get("key1")); // 输出 value1

        // 2. 自定义配置（例如使用 Caffeine，最大容量 1000，TTL 2 小时）
        CacheFactory.CacheConfigBean config = new CacheFactory.CacheConfigBean();
        config.setType(CacheType.CAFFEINE);
        config.setTtlMillis(2 * 60 * 60 * 1000L);
        config.setMaxSize(1000);
        Cache<String, String> highPerfCache = CacheFactory.createCache(config);
        highPerfCache.put("key2", "value2");
        System.out.println(highPerfCache.get("key2"));

        // 3. 使用 CacheManager 管理多个缓存实例
        CacheManager manager = CacheManager.getInstance();
        Cache<String, Integer> userCache = manager.getCache("userCache");
        userCache.computeIfAbsent("userId_123", k -> 456);
        System.out.println(userCache.get("userId_123")); // 456
    }
}