package com.omni.framework.expression.util;

import com.omni.framework.expression.ExpressionException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectUtils {
    // ★ 缓存Key = 方法名 + 参数类型全限定名（修复重载冲突）
    private static final Map<String, Method> staticMethodCache = new ConcurrentHashMap<>();

    private static final Class<?>[] CANDIDATE_CLASSES = {
            Math.class,
            String.class,
            Integer.class,
            Long.class,
            Double.class,
            Float.class,
            Boolean.class,
            Character.class,
            System.class,
            Object.class
    };

    private ReflectUtils() {}

    public static Object invokeStaticMethod(String methodName, Object... args) {
        if (args == null) args = new Object[0];
        String key = buildCacheKey(methodName, args);

        Method method = staticMethodCache.get(key);
        if (method == null) {
            method = findCompatibleStaticMethod(methodName, args);
            if (method != null) {
                staticMethodCache.put(key, method);
            }
        }

        if (method == null) {
            throw new ExpressionException("Static method not found: " + methodName +
                    " with " + args.length + " arguments");
        }

        try {
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new ExpressionException("Static method invocation failed: " + methodName, e);
        }
    }

    public static Object invokeMethod(Object target, String methodName, Object... args) {
        if (args == null) args = new Object[0];
        Class<?> clazz = target.getClass();
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                // 检查参数兼容性
                boolean match = true;
                for (int i = 0; i < args.length; i++) {
                    if (!isCompatible(m.getParameterTypes()[i], args[i].getClass())) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(target, args);
                    } catch (Exception e) {
                        throw new ExpressionException("Instance method invocation failed: " + methodName, e);
                    }
                }
            }
        }
        throw new ExpressionException("Method not found: " + methodName + " on " + clazz.getName());
    }

    // ==================== 私有辅助方法 ====================

    private static String buildCacheKey(String methodName, Object[] args) {
        StringBuilder sb = new StringBuilder(methodName);
        for (Object arg : args) {
            sb.append("#").append(arg.getClass().getName());
        }
        return sb.toString();
    }

    private static Method findCompatibleStaticMethod(String methodName, Object[] args) {
        for (Class<?> clazz : CANDIDATE_CLASSES) {
            Method[] methods = clazz.getMethods();
            for (Method m : methods) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterCount() != args.length) continue;

                boolean match = true;
                for (int i = 0; i < args.length; i++) {
                    if (!isCompatible(m.getParameterTypes()[i], args[i].getClass())) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * ★ 关键兼容检查：支持 int -> Integer, double -> Double 等基元/包装器互转
     */
    private static boolean isCompatible(Class<?> paramType, Class<?> argType) {
        if (paramType.isAssignableFrom(argType)) {
            return true;
        }
        // 基元与包装器兼容映射
        if (paramType.isPrimitive()) {
            if (paramType == int.class) return argType == Integer.class;
            if (paramType == long.class) return argType == Long.class;
            if (paramType == double.class) return argType == Double.class;
            if (paramType == float.class) return argType == Float.class;
            if (paramType == boolean.class) return argType == Boolean.class;
            if (paramType == char.class) return argType == Character.class;
            if (paramType == short.class) return argType == Short.class;
            if (paramType == byte.class) return argType == Byte.class;
        }
        return false;
    }
}