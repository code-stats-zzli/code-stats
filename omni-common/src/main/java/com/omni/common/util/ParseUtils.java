package com.omni.common.util;

/**
 * 类型解析 / 格式化工具类。
 * <p>所有 parse* 方法遇到非法输入返回默认值，不抛异常。</p>
 */
public final class ParseUtils {

    private ParseUtils() {}

    public static int parseInt(String s, int def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    public static long parseLong(String s, long def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    public static double parseDouble(String s, double def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    public static boolean parseBoolean(String s, boolean def) {
        if (s == null) return def;
        String t = s.trim().toLowerCase();
        if ("true".equals(t) || "1".equals(t) || "yes".equals(t) || "y".equals(t) || "on".equals(t)) return true;
        if ("false".equals(t) || "0".equals(t) || "no".equals(t) || "n".equals(t) || "off".equals(t)) return false;
        return def;
    }

    /** 格式化字节数为人类可读字符串：1.5 MB / 2.3 GB */
    public static String formatSize(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024);
        return String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024);
    }

    /** 格式化毫秒为可读时长：1天2时3分4秒 */
    public static String formatDuration(long ms) {
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600;  s %= 3600;
        long m = s / 60;    s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("天 ");
        if (h > 0 || sb.length() > 0) sb.append(h).append("时 ");
        sb.append(m).append("分 ").append(s).append("秒");
        return sb.toString();
    }
}