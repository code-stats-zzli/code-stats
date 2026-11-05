package com.omni.business.assistance.importfile.parser;

/** 路径归一化：所有解析器共用 */
public final class PathNormalizer {
    private PathNormalizer() {}

    /**
     * 归一化路径；非法返回 null
     * - 反斜杠转 /
     * - 去掉前导 /
     * - 拒绝 .. 和盘符
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String p = raw.trim().replace('\\', '/');
        if ((p.startsWith("\"") && p.endsWith("\""))
                || (p.startsWith("'") && p.endsWith("'"))) {
            p = p.substring(1, p.length() - 1);
        }
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return null;
        if (p.contains("..")) return null;
        if (p.matches("^[A-Za-z]:.*")) return null;
        if (p.contains("\u0000")) return null;
        return p;
    }
}