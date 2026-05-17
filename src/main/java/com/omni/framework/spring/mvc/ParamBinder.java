package com.omni.framework.spring.mvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.framework.spring.annotation.PathVariable;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class ParamBinder {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Object[] resolveParameters(Method method, HttpServletRequest request, HttpServletResponse response,
                                             Map<String, String> pathVars) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        Map<String, String> queryParams = parseQueryParams(request);

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> type = param.getType();

            // 注入 HttpServletRequest / HttpServletResponse
            if (type == HttpServletRequest.class) {
                args[i] = request;
                continue;
            }
            if (type == HttpServletResponse.class) {
                args[i] = response;
                continue;
            }

            // @PathVariable
            PathVariable pathVar = param.getAnnotation(PathVariable.class);
            if (pathVar != null) {
                String name = pathVar.value().isEmpty() ? param.getName() : pathVar.value();
                String value = pathVars.get(name);
                args[i] = convert(value, type);
                continue;
            }

            // @RequestParam
            RequestParam reqParam = param.getAnnotation(RequestParam.class);
            if (reqParam != null) {
                String name = reqParam.value().isEmpty() ? param.getName() : reqParam.value();
                String value = queryParams.get(name);
                if (value == null) {
                    if (reqParam.required()) {
                        throw new IllegalArgumentException("Missing required parameter: " + name);
                    }
                    value = reqParam.defaultValue();
                }
                args[i] = convert(value, type);
                continue;
            }

            // @RequestBody (新增支持)
            RequestBody requestBody = param.getAnnotation(RequestBody.class);
            if (requestBody != null) {
                String body = request.getBody();
                if ((body == null || body.trim().isEmpty()) && requestBody.required()) {
                    throw new IllegalArgumentException("Missing request body");
                }
                if (body != null && !body.trim().isEmpty()) {
                    args[i] = type == String.class ? body :objectMapper.readValue(body, type);
                } else {
                    args[i] = null;
                }
                continue;
            }

            // 无注解但为简单类型，尝试从 query 中取同名参数
            if (isSimpleType(type)) {
                String value = queryParams.get(param.getName());
                if (value != null) {
                    args[i] = convert(value, type);
                } else {
                    args[i] = getDefaultValue(type);
                }
                continue;
            }

            args[i] = null;
        }
        return args;
    }

    private static Map<String, String> parseQueryParams(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private static boolean isSimpleType(Class<?> clazz) {
        return clazz == String.class ||
                clazz == int.class || clazz == Integer.class ||
                clazz == long.class || clazz == Long.class ||
                clazz == boolean.class || clazz == Boolean.class ||
                clazz == double.class || clazz == Double.class ||
                clazz == float.class || clazz == Float.class;
    }

    private static Object convert(String str, Class<?> targetType) {
        if (str == null) return null;
        if (targetType == String.class) return str;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(str);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(str);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(str);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(str);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(str);
        throw new IllegalArgumentException("Unsupported type: " + targetType);
    }

    private static Object getDefaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == float.class || type == Float.class) return 0.0f;
        return null;
    }
}