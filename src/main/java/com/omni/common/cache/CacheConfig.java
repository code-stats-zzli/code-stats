package com.omni.common.cache;

/**
 * 缓存全局配置
 */
public class CacheConfig {

    /** 默认过期时间（毫秒），1天 */
    public static final long DEFAULT_TTL_MILLIS = 24 * 60 * 60 * 1000L;

    /** 默认最大容量（条数），-1 表示无限制 */
    public static final long DEFAULT_MAX_SIZE = -1;

    /** 默认缓存类型：LOCAL（基于 ConcurrentHashMap） */
    public static final CacheType DEFAULT_CACHE_TYPE = CacheType.LOCAL;

    /** 是否启用定期清理后台线程（仅对 LOCAL 类型有效） */
    public static final boolean DEFAULT_ENABLE_CLEANER = true;

    /** 清理间隔（秒），默认 30 秒 */
    public static final long DEFAULT_CLEAN_INTERVAL_SECONDS = 30L;
}