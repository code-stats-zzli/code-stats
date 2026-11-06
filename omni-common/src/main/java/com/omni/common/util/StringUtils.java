package com.omni.common.util;

/**
 * 字符串工具类（纯 JDK，无依赖）。
 * <p>主要被 Controller / Service / 连接串解析等使用。</p>
 */
public final class StringUtils {

    private StringUtils() {}

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static boolean isNotEmpty(String s) {
        return !isEmpty(s);
    }

    /** null 安全：null 返回 null，否则返回 String.valueOf */
    public static String safe(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** null 安全 + 默认值 */
    public static String safe(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o);
        return s.isEmpty() ? def : s;
    }

    /** 非空白校验：为空白抛 IllegalArgumentException */
    public static String requireNonBlank(String s, String message) {
        if (isBlank(s)) throw new IllegalArgumentException(message);
        return s;
    }

    public static String requireNonBlank(String s) {
        return requireNonBlank(s, "参数不能为空");
    }

    /** 首字母转小写 */
    public static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** 首字母转大写 */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** 下划线转驼峰 */
    public static String underscoreToCamel(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : name.toCharArray()) {
            if (c == '_') nextUpper = true;
            else { sb.append(nextUpper ? Character.toUpperCase(c) : c); nextUpper = false; }
        }
        return sb.toString();
    }

    /** 驼峰转下划线 */
    public static String camelToUnderscore(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** 逗号分隔字符串拆分为列表（去空白、过滤空串） */
    public static java.util.List<String> splitTrim(String s, String sep) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (isBlank(s)) return out;
        for (String t : s.split(java.util.regex.Pattern.quote(sep))) {
            String x = t.trim();
            if (!x.isEmpty()) out.add(x);
        }
        return out;
    }

    /** 安全截断 */
    public static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}