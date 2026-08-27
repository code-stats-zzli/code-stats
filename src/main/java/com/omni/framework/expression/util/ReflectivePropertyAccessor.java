package com.omni.framework.expression.util;

import com.omni.framework.expression.ExpressionException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优化版属性访问器：
 * 1. 增加 Getter/Setter/Field 三层缓存，大幅提升反射性能
 * 2. Setter 参数支持 isAssignableFrom 匹配（ArrayList -> List）
 * 3. 保留私有字段/方法强制访问能力
 */
public class ReflectivePropertyAccessor implements PropertyAccessor {

    // ★ 三层缓存
    private final Map<Class<?>, Map<String, Method>> getterCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, Method>> setterCache = new ConcurrentHashMap<>();

    @Override
    public Object getProperty(Object target, String property) throws ExpressionException {
        if (target == null) {
            throw new ExpressionException("Cannot get property on null object");
        }
        // Map 类型直接获取
        if (target instanceof Map) {
            return ((Map<?, ?>) target).get(property);
        }

        Class<?> clazz = target.getClass();
        String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);

        // 1. 从缓存获取 Getter
        Method getter = getGetterFromCache(clazz, property, getterName);
        if (getter != null) {
            try {
                return getter.invoke(target);
            } catch (Exception e) {
                throw new ExpressionException("Failed to invoke getter: " + getterName, e);
            }
        }

        // 2. 尝试布尔类型的 isXxx
        if (property.startsWith("is") && property.length() > 2) {
            Method isGetter = getGetterFromCache(clazz, property, property);
            if (isGetter != null) {
                try {
                    return isGetter.invoke(target);
                } catch (Exception e) {
                    throw new ExpressionException("Failed to invoke is-getter: " + property, e);
                }
            }
        }

        // 3. 回退到字段访问
        Field field = getFieldFromCache(clazz, property);
        if (field != null) {
            try {
                return field.get(target);
            } catch (IllegalAccessException e) {
                throw new ExpressionException("Failed to access field: " + property, e);
            }
        }

        throw new ExpressionException("Property not found: " + property + " on " + clazz.getName());
    }

    @Override
    public void setProperty(Object target, String property, Object value) throws ExpressionException {
        if (target == null) {
            throw new ExpressionException("Cannot set property on null object");
        }
        if (target instanceof Map) {
            ((Map<Object, Object>) target).put(property, value);
            return;
        }

        Class<?> clazz = target.getClass();
        Class<?> valueType = (value != null) ? value.getClass() : Object.class;
        String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);

        // 1. 从缓存获取 Setter
        Method setter = getSetterFromCache(clazz, property, setterName, valueType);
        if (setter != null) {
            try {
                setter.invoke(target, value);
                return;
            } catch (Exception e) {
                throw new ExpressionException("Failed to invoke setter: " + setterName, e);
            }
        }

        // 2. 回退到字段赋值
        Field field = getFieldFromCache(clazz, property);
        if (field != null) {
            try {
                field.set(target, value);
                return;
            } catch (IllegalAccessException e) {
                throw new ExpressionException("Failed to set field: " + property, e);
            }
        }

        throw new ExpressionException("Cannot set property: " + property + " on " + clazz.getName());
    }

    // ==================== 缓存辅助方法 ====================

    private Method getGetterFromCache(Class<?> clazz, String property, String methodName) {
        Map<String, Method> cache = getterCache.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        return cache.computeIfAbsent(property, k -> {
            try {
                Method m = clazz.getMethod(methodName);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                return null;
            }
        });
    }

    private Method getSetterFromCache(Class<?> clazz, String property, String methodName, Class<?> valueType) {
        Map<String, Method> cache = setterCache.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        // ★ 缓存Key包含参数类型，避免错误复用
        String cacheKey = property + "#" + valueType.getName();
        return cache.computeIfAbsent(cacheKey, k -> {
            Method[] methods = clazz.getMethods();
            for (Method m : methods) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    // ★ 关键修复：使用 isAssignableFrom 支持子类参数
                    if (paramType.isAssignableFrom(valueType)) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
            return null;
        });
    }

    private Field getFieldFromCache(Class<?> clazz, String property) {
        Map<String, Field> cache = fieldCache.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        return cache.computeIfAbsent(property, k -> {
            try {
                Field f = clazz.getDeclaredField(property);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                return null;
            }
        });
    }
}