package com.omni.framework.spring.mvc;

import com.omni.framework.spring.annotation.*;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

public class ParamBinder {

    public static Object[] resolveParameters(Method method, Request request, Response response, Map<String, String> pathVariables) throws Exception {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Class<?> type = p.getType();

            if (type == Request.class) {
                args[i] = request;
                continue;
            }
            if (type == Response.class) {
                args[i] = response;
                continue;
            }

            // @PathVariable
            PathVariable pathVar = p.getAnnotation(PathVariable.class);
            if (pathVar != null) {
                String name = pathVar.value();
                String value = pathVariables.get(name);
                if (value == null && pathVar.required()) {
                    throw new IllegalArgumentException("Missing path variable: " + name);
                }
                args[i] = convert(value, type);
                continue;
            }

            // @RequestParam
            RequestParam reqParam = p.getAnnotation(RequestParam.class);
            if (reqParam != null) {
                String name = reqParam.value();
                String value = request.getParameters().get(name);
                if (value == null && reqParam.required()) {
                    if (!reqParam.defaultValue().isEmpty()) {
                        value = reqParam.defaultValue();
                    } else {
                        throw new IllegalArgumentException("Missing request parameter: " + name);
                    }
                }
                args[i] = convert(value, type);
                continue;
            }

            // @RequestPart (multipart)
            RequestPart part = p.getAnnotation(RequestPart.class);
            if (part != null) {
                String name = part.value();
                if (request.getMultipartResolver() == null) {
                    throw new IllegalArgumentException("Not a multipart request");
                }
                byte[] data = request.getMultipartResolver().getFileParameter(name);
                if (data == null && part.required()) {
                    throw new IllegalArgumentException("Missing request part: " + name);
                }
                if (type == byte[].class) {
                    args[i] = data;
                } else if (type == String.class && data != null) {
                    args[i] = new String(data);
                } else if (type == java.io.InputStream.class && data != null) {
                    args[i] = new ByteArrayInputStream(data);
                } else {
                    // 可以扩展为文件对象等
                    args[i] = data;
                }
                continue;
            }

            // @RequestBody
            RequestBody body = p.getAnnotation(RequestBody.class);
            if (body != null) {
                String ct = request.getContentType();
                if (ct == null && body.required()) {
                    throw new IllegalArgumentException("Missing Content-Type for @RequestBody");
                }
                byte[] bodyBytes = request.getBody() != null ? request.getBody().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1) : new byte[0];
                Object converted = MessageConverters.readFromRequest(new ByteArrayInputStream(bodyBytes), type, ct);
                args[i] = converted;
                continue;
            }

            // 没有注解，赋 null
            args[i] = null;
        }
        return args;
    }

    private static Object convert(String str, Class<?> targetType) {
        if (str == null) return null;
        if (targetType == String.class) return str;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(str);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(str);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(str);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(str);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(str);
        throw new IllegalArgumentException("Unsupported type conversion: " + targetType);
    }
}