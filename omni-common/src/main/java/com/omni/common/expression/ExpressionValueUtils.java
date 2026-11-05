package com.omni.common.expression;

import java.util.Collection;
import java.util.Map;

/**
 * 表达式值处理工具（纯 JDK，无框架/业务依赖）。
 * 负责：
 *   1. 原始字符串推断为类型化对象（数字/布尔/null/JSON/字符串）
 *   2. 求值结果格式化（整数 Double 去 .0）
 *   3. 结果类型简单名称
 */
public final class ExpressionValueUtils {

    private ExpressionValueUtils() {}

    /**
     * 将用户输入的原始字符串推断为类型化值：
     *   "" / null          → null
     *   "null"             → null
     *   "true"/"false"     → Boolean
     *   "123"/"-123"       → Long
     *   "3.14"             → Double
     *   "[1,2]"/"{...}"    → List / Map（JSON 解析失败则回退字符串）
     *   其他               → String
     */
    public static Object parseRawValue(Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof CharSequence)) return raw;
        String s = raw.toString().trim();
        if (s.isEmpty()) return "";
        if ("null".equalsIgnoreCase(s)) return null;
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        if (s.matches("-?\\d+")) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        if (s.matches("-?\\d+\\.\\d+([eE][+-]?\\d+)?")) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return s;
    }

    /**
     * 求值结果转为可展示对象：
     *   Double 整数值 → Long（避免 "5.0"）
     *   复杂对象（List/Map）保留，前端 JSON 展示
     *   其他 Number/Boolean/String 原样
     */
    public static Object toDisplay(Object result) {
        if (result == null) return null;
        if (result instanceof Double) {
            double d = (Double) result;
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return (long) d;
            }
            return d;
        }
        if (result instanceof Collection || result instanceof Map) {
            return result;
        }
        if (result instanceof Number || result instanceof Boolean
                || result instanceof CharSequence) {
            return result;
        }
        return String.valueOf(result);
    }

    /**
     * 结果类型简单名（前端用于格式化展示）。
     */
    public static String simpleTypeName(Object r) {
        if (r == null) return "null";
        if (r instanceof Boolean) return "boolean";
        if (r instanceof Number) return "number";
        if (r instanceof CharSequence) return "string";
        if (r instanceof Collection) return "array";
        if (r instanceof Map) return "object";
        return r.getClass().getSimpleName();
    }
}