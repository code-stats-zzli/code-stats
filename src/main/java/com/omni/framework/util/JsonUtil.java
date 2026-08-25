package com.omni.framework.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 轻量级 JSON 序列化工具，增强版支持任意 JavaBean 的反射序列化。
 * 支持 String、Number、Boolean、null、Map、Collection、Array 以及普通 Java 对象。
 * 自动忽略 static 和 transient 字段，防止循环引用导致栈溢出。
 */
public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将任意 Java 对象序列化为 JSON 字符串。
     * @param obj 要序列化的对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj)  {
        if(obj == null)return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 递归序列化核心方法
     * @param obj 当前对象
     * @param sb 输出缓冲区
     * @param refs 已序列化对象记录（用于检测循环引用）
     */
    @SuppressWarnings("unchecked")
    private static void serialize(Object obj, StringBuilder sb, IdentityHashMap<Object, Boolean> refs) {
        if (obj == null) {
            sb.append("null");
            return;
        }

        // 检测循环引用：如果已经序列化过该对象，输出 null 或引用标记（这里输出 null 避免无限递归）
        if (refs.containsKey(obj)) {
            sb.append("null");
            return;
        }

        // 基本类型和常用类型处理
        if (obj instanceof String) {
            sb.append('"').append(escape((String) obj)).append('"');
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
        } else if (obj instanceof Map) {
            refs.put(obj, Boolean.TRUE);
            serializeMap((Map<String, Object>) obj, sb, refs);
            refs.remove(obj);
        } else if (obj instanceof Collection) {
            refs.put(obj, Boolean.TRUE);
            serializeCollection((Collection<?>) obj, sb, refs);
            refs.remove(obj);
        } else if (obj.getClass().isArray()) {
            refs.put(obj, Boolean.TRUE);
            serializeArray(obj, sb, refs);
            refs.remove(obj);
        } else {
            // 普通 Java 对象，通过反射获取字段并序列化为 JSON 对象
            refs.put(obj, Boolean.TRUE);
            serializeBean(obj, sb, refs);
            refs.remove(obj);
        }
    }

    /**
     * 序列化 Map 对象
     */
    private static void serializeMap(Map<String, Object> map, StringBuilder sb, IdentityHashMap<Object, Boolean> refs) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(entry.getKey())).append("\":");
            serialize(entry.getValue(), sb, refs);
            first = false;
        }
        sb.append('}');
    }

    /**
     * 序列化 Collection 对象
     */
    private static void serializeCollection(Collection<?> coll, StringBuilder sb, IdentityHashMap<Object, Boolean> refs) {
        sb.append('[');
        boolean first = true;
        for (Object item : coll) {
            if (!first) sb.append(',');
            serialize(item, sb, refs);
            first = false;
        }
        sb.append(']');
    }

    /**
     * 序列化数组
     */
    private static void serializeArray(Object array, StringBuilder sb, IdentityHashMap<Object, Boolean> refs) {
        sb.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(',');
            serialize(Array.get(array, i), sb, refs);
        }
        sb.append(']');
    }

    /**
     * 序列化普通 JavaBean：通过反射获取所有字段（包括私有字段），忽略 static 和 transient
     */
    private static void serializeBean(Object bean, StringBuilder sb, IdentityHashMap<Object, Boolean> refs) {
        Class<?> clazz = bean.getClass();
        Field[] fields = clazz.getDeclaredFields();
        sb.append('{');
        boolean first = true;
        for (Field field : fields) {
            // 忽略静态字段和 transient 字段
            int mod = field.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(bean);
                // 可选：跳过值为 null 的字段（按需求可保留）
                // if (value == null) continue;
                if (!first) sb.append(',');
                sb.append('"').append(escape(field.getName())).append("\":");
                serialize(value, sb, refs);
                first = false;
            } catch (IllegalAccessException e) {
                // 忽略无法访问的字段
            }
        }
        sb.append('}');
    }

    /**
     * JSON 字符串转义（处理反斜杠、双引号、换行等）
     */
    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }



    // 原有 toJson 方法保持不变 ...

    /**
     * 将 JSON 字符串解析为 Map<String, Object>
     * @param json JSON 字符串
     * @return 解析后的 Map，若解析失败返回空 Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            // 降级：尝试使用简单的手工解析（兼容原有简单格式）
            return simpleParseJson(json);
        }
    }

    /**
     * 将 JSON 字符串解析为指定类型的对象
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @return 解析后的对象
     */
    public static <T> T parseJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败: " + json, e);
        }
    }

    /**
     * 简单的 JSON 解析（仅支持扁平结构，作为 Jackson 失败时的降级）
     */
    private static Map<String, Object> simpleParseJson(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            if (inner.isEmpty()) return map;
            // 按逗号分割，但要注意字符串内的逗号不分割（简单实现不处理复杂嵌套）
            String[] pairs = inner.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String value = kv[1].trim().replaceAll("^\"|\"$", "");
                    map.put(key, value);
                }
            }
        }
        return map;
    }
}