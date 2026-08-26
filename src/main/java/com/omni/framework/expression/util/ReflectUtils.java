package com.omni.framework.expression.util;

import com.omni.framework.expression.ExpressionException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectUtils {
    private static final Map<String, Method> staticMethodCache = new ConcurrentHashMap<>();

    private ReflectUtils() {}

    public static Object invokeStaticMethod(String methodName, Object... args) {
        // 尝试从缓存获取
        String key = methodName + "#" + args.length;
        Method method = staticMethodCache.get(key);
        if (method != null) {
            try {
                return method.invoke(null, args);
            } catch (Exception e) {
                throw new ExpressionException("Static method invocation failed: " + methodName, e);
            }
        }

        // 遍历常用类
        Class<?>[] candidateClasses = {
                Math.class,
                String.class,
                Integer.class,
                Long.class,
                Double.class,
                Boolean.class,
                Character.class,
                System.class,
                Object.class
        };
        for (Class<?> clazz : candidateClasses) {
            Method[] methods = clazz.getMethods();
            for (Method m : methods) {
                if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    // 简单参数类型匹配（忽略精确匹配，直接调用，JVM会处理）
                    staticMethodCache.put(key, m);
                    try {
                        return m.invoke(null, args);
                    } catch (Exception e) {
                        throw new ExpressionException("Static method invocation failed: " + methodName, e);
                    }
                }
            }
        }
        throw new ExpressionException("Static method not found: " + methodName + " with " + args.length + " arguments");
    }

    public static Object invokeMethod(Object target, String methodName, Object... args) {
        Class<?> clazz = target.getClass();
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                try {
                    return m.invoke(target, args);
                } catch (Exception e) {
                    throw new ExpressionException("Instance method invocation failed: " + methodName, e);
                }
            }
        }
        throw new ExpressionException("Method not found: " + methodName + " on " + clazz.getName());
    }
}