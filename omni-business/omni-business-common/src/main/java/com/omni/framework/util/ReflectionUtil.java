package com.omni.framework.util;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射工具类 – 禁止使用 Class.newInstance()，统一通过 Constructor 实例化
 */
public final class ReflectionUtil {
    private static final Logger log = LoggerFactory.getLogger(ReflectionUtil.class);

    // 构造器缓存
    private static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    // 接口 -> 默认实现映射
    private static final Map<Class<?>, Class<?>> DEFAULT_IMPL_MAP = new HashMap<>();
    static {
        DEFAULT_IMPL_MAP.put(List.class, ArrayList.class);
        DEFAULT_IMPL_MAP.put(Set.class, HashSet.class);
        DEFAULT_IMPL_MAP.put(Map.class, HashMap.class);
        DEFAULT_IMPL_MAP.put(Collection.class, ArrayList.class);
        DEFAULT_IMPL_MAP.put(Queue.class, LinkedList.class);
        DEFAULT_IMPL_MAP.put(Deque.class, ArrayDeque.class);
    }

    private ReflectionUtil() {}

    /**
     * 创建实例 – 优先无参构造，支持接口回退（避免直接 Class.newInstance）
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clazz) {
        // 接口/抽象类处理
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            Class<?> implClass = DEFAULT_IMPL_MAP.get(clazz);
            if (implClass != null) {
                return (T) newInstance(implClass);
            }
            throw new RuntimeException("无法实例化接口/抽象类: " + clazz.getName() +
                    "，请使用具体实现类或注册默认实现");
        }

        // 从缓存获取构造器
        Constructor<T> constructor = (Constructor<T>) CONSTRUCTOR_CACHE.get(clazz);
        if (constructor == null) {
            try {
                constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                CONSTRUCTOR_CACHE.put(clazz, constructor);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("类 " + clazz.getName() + " 缺少无参构造方法", e);
            }
        }

        try {
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("实例化类 " + clazz.getName() + " 失败", e);
        }
    }

    /**
     * 安全实例化（失败返回 null，不抛异常）
     */
    public static <T> T newInstanceSafely(Class<T> clazz) {
        try {
            return newInstance(clazz);
        } catch (Exception e) {
            log.error("实例化类 {} 失败", clazz.getName(), e);
            return null;
        }
    }

    // 其他辅助方法（setFieldValue, getFieldValue 等）保持不变
}