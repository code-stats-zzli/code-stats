package com.omni.framework.spring.mvc;

import com.omni.framework.util.FileUtils;
import com.omni.framework.util.JsonUtil;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MessageConverters {
    private static final List<HttpMessageConverter<?>> converters = new ArrayList<>();

    static {
        converters.add(new StringHttpMessageConverter());
        converters.add(new JsonHttpMessageConverter());
        // 可扩展 XML、Form 等
    }

    public static List<HttpMessageConverter<?>> getConverters() {
        return converters;
    }

    // 根据请求 Content-Type 找到合适的读取器
    public static Object readFromRequest(InputStream input, Class<?> targetType, String contentType) throws Exception {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter.canRead(targetType, contentType)) {
                return converter.read(input, targetType);
            }
        }
        throw new UnsupportedOperationException("No converter for content-type: " + contentType);
    }

    // 根据响应 Accept 或默认输出类型找到写入器
    public static void writeToResponse(Object obj, OutputStream output, String acceptType) throws Exception {
        Class<?> clazz = obj.getClass();
        for (HttpMessageConverter<?> converter : converters) {
            if (converter.canWrite(clazz, acceptType)) {
                ((HttpMessageConverter<Object>) converter).write(obj, output, acceptType);
                return;
            }
        }
        // fallback: 调用 toString
        output.write(obj.toString().getBytes(StandardCharsets.UTF_8));
    }

    // 纯文本转换器
    static class StringHttpMessageConverter implements HttpMessageConverter<String> {
        @Override
        public boolean canRead(Class<?> clazz, String contentType) {
            return clazz == String.class && (contentType == null || contentType.startsWith("text/plain"));
        }
        @Override
        public String read(InputStream inputStream, Class<?> clazz) throws IOException {
            return FileUtils.readInputStreamToString(inputStream);
        }
        @Override
        public boolean canWrite(Class<?> clazz, String acceptType) {
            return clazz == String.class && (acceptType == null || acceptType.startsWith("text/plain"));
        }
        @Override
        public void write(String obj, OutputStream outputStream, String contentType) throws IOException {
            outputStream.write(obj.getBytes(StandardCharsets.UTF_8));
        }
    }

    // JSON 转换器（基于项目已有的 JsonUtil）
    static class JsonHttpMessageConverter implements HttpMessageConverter<Object> {
        @Override
        public boolean canRead(Class<?> clazz, String contentType) {
            return contentType != null && contentType.startsWith("application/json");
        }
        @Override
        public Object read(InputStream inputStream, Class<?> clazz) throws IOException {
            String json = FileUtils.readInputStreamToString(inputStream);
            // 简易反序列化：此处仅演示，实际可用 Jackson 或反射
            // 由于 JsonUtil 只提供序列化，这里简单返回字符串，用户可根据需要扩展
            return json;
        }
        @Override
        public boolean canWrite(Class<?> clazz, String acceptType) {
            return acceptType == null || acceptType.startsWith("application/json") || acceptType.equals("*/*");
        }
        @Override
        public void write(Object obj, OutputStream outputStream, String contentType) throws IOException {
            //String json = JsonUtil.toJson(obj);
            String json;
            if (obj instanceof String) {
                String str = (String) obj;
                String trimmed = str.trim();
                // 如果已经是 JSON 字符串（以 { 或 [ 开头），则直接使用
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    json = str;
                } else {
                    json = JsonUtil.toJson(obj);
                }
            } else {
                json = JsonUtil.toJson(obj);
            }
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}