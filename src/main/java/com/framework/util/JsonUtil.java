package com.framework.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * 轻量级 JSON 序列化工具，模仿主流 JSON 库的用法。
 * 支持 String、Number、Boolean、null、Map、Collection、数组以及嵌套对象。
 */
public class JsonUtil {

    /**
     * 将任意 Java 对象序列化为 JSON 字符串。
     * @param obj 要序列化的对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        StringBuilder sb = new StringBuilder();
        serialize(obj, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void serialize(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof String) {
            sb.append('"').append(escape((String) obj)).append('"');
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
        } else if (obj instanceof Map) {
            serializeMap((Map<String, Object>) obj, sb);
        } else if (obj instanceof Collection) {
            serializeCollection((Collection<?>) obj, sb);
        } else if (obj.getClass().isArray()) {
            serializeArray(obj, sb);
        } else {
            // 其他复杂对象，暂时转为字符串表示（可扩展为反射获取字段）
            sb.append('"').append(escape(obj.toString())).append('"');
        }
    }

    private static void serializeMap(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escape(entry.getKey())).append("\":");
            serialize(entry.getValue(), sb);
            first = false;
        }
        sb.append('}');
    }

    private static void serializeCollection(Collection<?> coll, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object item : coll) {
            if (!first) {
                sb.append(',');
            }
            serialize(item, sb);
            first = false;
        }
        sb.append(']');
    }

    private static void serializeArray(Object array, StringBuilder sb) {
        sb.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(',');
            }
            serialize(Array.get(array, i), sb);
        }
        sb.append(']');
    }

    /**
     * JSON 字符串转义（处理反斜杠、双引号、换行等）
     */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
}