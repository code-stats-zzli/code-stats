package com.omni.common.util;

/**
 * 敏感信息脱敏工具。
 */
public final class MaskUtils {

    private MaskUtils() {}

    /**
     * 脱敏 URL：保留协议头，其余替换为 ***。
     * 例：jdbc:mysql://user:pwd@host:3306/db → jdbc:mysql://***
     */
    public static String maskUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        int idx = url.indexOf("://");
        if (idx > 0 && url.length() > idx + 8) {
            return url.substring(0, idx + 3) + "***";
        }
        return url;
    }

    /** 脱敏密码：保留首尾各 1 个字符 */
    public static String maskPassword(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() <= 2) return "***";
        return s.charAt(0) + "***" + s.charAt(s.length() - 1);
    }

    /** 脱敏 token：保留前 4 + 后 4 */
    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) return token;
        if (token.length() <= 8) return "******";
        return token.substring(0, 4) + "******" + token.substring(token.length() - 4);
    }

    /** 脱敏中间部分 */
    public static String maskMiddle(String s, int keep) {
        if (s == null) return null;
        if (s.length() <= keep * 2) return "***";
        return s.substring(0, keep) + "***" + s.substring(s.length() - keep);
    }
}