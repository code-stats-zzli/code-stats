package com.framework.spring.mvc;

import com.framework.tomcat.connector.Request;
import com.framework.spring.annotation.RequestParam;
import com.framework.tomcat.connector.Response;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * 参数绑定工具：支持从 Request 中提取参数并转换为方法参数值
 * 支持基本类型、String、包装类以及任意 JavaBean（通过字段名匹配）
 */
public class ParamBinder {

    /**
     * 解析方法参数，返回调用时需传入的参数数组
     * @param method 目标方法
     * @param request 当前 HTTP 请求
     * @return 参数值数组（顺序与 method.getParameters() 一致）
     * @throws Exception 参数缺失或类型转换失败时抛出
     */
    public static Object[] resolveParameters(Method method, Request request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        Map<String, String> paramMap = getParamMap(request);

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();

            // 处理 @RequestParam
            RequestParam requestParam = param.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                String paramName = requestParam.value();
                String strValue = paramMap.get(paramName);
                if (strValue == null || strValue.isEmpty()) {
                    if (requestParam.required()) {
                        throw new IllegalArgumentException("Missing required parameter: " + paramName);
                    } else {
                        strValue = requestParam.defaultValue();
                    }
                }
                args[i] = convert(strValue, paramType);
                continue;
            }

            // 特殊对象注入
            if (paramType == Request.class) {
                args[i] = request;
                continue;
            }
            if (paramType == Response.class) {
                // Response 也可注入（通过 ResponseContextHolder 获取，但直接传入更灵活）
                args[i] = ResponseContextHolder.getResponse();
                continue;
            }

            // 对于没有注解的参数，且不是框架对象，则赋 null（不自动绑定业务参数）
            args[i] = null;
        }
        return args;
    }

    /**
     * 从 Request 中获取参数 Map
     */
    private static Map<String, String> getParamMap(Request request) {
        Map<String, String> map = new HashMap<>();
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return map;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    /**
     * 判断是否简单类型（可自动转换）
     */
    private static boolean isSimpleType(Class<?> clazz) {
        return clazz == String.class ||
                clazz == int.class || clazz == Integer.class ||
                clazz == long.class || clazz == Long.class ||
                clazz == boolean.class || clazz == Boolean.class ||
                clazz == double.class || clazz == Double.class ||
                clazz == float.class || clazz == Float.class ||
                clazz == short.class || clazz == Short.class ||
                clazz == byte.class || clazz == Byte.class;
    }

    /**
     * 字符串转简单类型
     */
    private static Object convert(String str, Class<?> targetType) {
        if (str == null) return null;
        if (targetType == String.class) return str;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(str);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(str);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(str);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(str);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(str);
        if (targetType == short.class || targetType == Short.class) return Short.parseShort(str);
        if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(str);
        throw new IllegalArgumentException("Unsupported type: " + targetType);
    }

    /**
     * 获取简单类型默认值
     */
    private static Object getDefaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == float.class || type == Float.class) return 0.0f;
        if (type == short.class || type == Short.class) return (short)0;
        if (type == byte.class || type == Byte.class) return (byte)0;
        return null;
    }

    /**
     * 将请求参数 Map 绑定到 JavaBean 对象（仅支持基本类型字段）
     */
    private static Object bindObject(Class<?> beanClass, Map<String, String> paramMap) throws Exception {
        Object instance = beanClass.getDeclaredConstructor().newInstance();
        Field[] fields = beanClass.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            String strValue = paramMap.get(fieldName);
            if (strValue != null && !strValue.isEmpty()) {
                field.setAccessible(true);
                Object value = convert(strValue, field.getType());
                field.set(instance, value);
            }
        }
        return instance;
    }
}