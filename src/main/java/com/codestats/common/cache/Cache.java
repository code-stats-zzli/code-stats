package com.codestats.common.cache;

import java.util.function.Function;

/**
 * 本地缓存统一接口
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface Cache<K, V> {

    /**
     * 放入缓存，使用默认 TTL
     */
    void put(K key, V value);

    /**
     * 放入缓存，自定义 TTL（毫秒）
     */
    void put(K key, V value, long ttlMillis);

    /**
     * 获取缓存值，若已过期则返回 null
     */
    V get(K key);

    /**
     * 获取缓存值，若不存在则通过 loader 加载并缓存（使用默认 TTL）
     */
    V computeIfAbsent(K key, Function<K, V> loader);

    /**
     * 移除指定键
     */
    void remove(K key);

    /**
     * 清空所有缓存
     */
    void clear();

    /**
     * 获取当前缓存大小
     */
    long size();

    /**
     * 检查是否包含键（且未过期）
     */
    boolean containsKey(K key);
}