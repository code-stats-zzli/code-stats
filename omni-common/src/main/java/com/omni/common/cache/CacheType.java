package com.omni.common.cache;

/**
 * 支持的缓存类型
 */
public enum CacheType {
    /**
     * 基于 ConcurrentHashMap 的本地缓存
     */
    LOCAL,

    /**
     * 基于 Caffeine 的高性能缓存（需要引入 caffeine 依赖）
     */
    CAFFEINE
}