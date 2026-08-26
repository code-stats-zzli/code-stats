package com.omni.framework.expression.util;

import com.omni.framework.expression.ExpressionException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 基于反射的属性访问器，支持：
 * - 通过 getter/setter 访问
 * - 直接访问字段（私有字段也会通过 setAccessible 强制访问）
 * - Map 类型直接操作
 * - 自动处理非 public 类（内部类等）
 */
public class ReflectivePropertyAccessor implements PropertyAccessor {

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

        // 1. 尝试 getter 方法
        try {
            Method method = clazz.getMethod(getterName);
            method.setAccessible(true);   // ★ 关键修复：允许访问非 public 类中的方法
            return method.invoke(target);
        } catch (NoSuchMethodException e) {
            // 2. 尝试 isXxx 格式（布尔类型）
            if (property.startsWith("is") && property.length() > 2) {
                try {
                    Method method = clazz.getMethod(property);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ex) {
                    // ignore
                } catch (InvocationTargetException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
            // 3. 回退到直接字段访问
            try {
                Field field = clazz.getDeclaredField(property);
                field.setAccessible(true);   // 强制访问私有字段
                return field.get(target);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new ExpressionException("Property not found: " + property + " on " + clazz.getName(), ex);
            }
        } catch (Exception e) {
            throw new ExpressionException("Failed to access property: " + property, e);
        }
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
        String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);

        // 1. 尝试 setter 方法
        try {
            // 参数类型使用 Object.class 或 value 的实际类型，但用 Object.class 更通用
            Method method = clazz.getMethod(setterName, value != null ? value.getClass() : Object.class);
            method.setAccessible(true);   // ★ 关键修复
            method.invoke(target, value);
            return;
        } catch (NoSuchMethodException e) {
            // 2. 回退到直接字段赋值
            try {
                Field field = clazz.getDeclaredField(property);
                field.setAccessible(true);   // 强制访问私有字段
                field.set(target, value);
                return;
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new ExpressionException("Cannot set property: " + property + " on " + clazz.getName(), ex);
            }
        } catch (Exception e) {
            throw new ExpressionException("Failed to set property: " + property, e);
        }
    }
}