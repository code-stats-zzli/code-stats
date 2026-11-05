package com.omni.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 驱动 jar 文件名解析器（前后端规则一致）。
 *
 * <p>支持的 4 种类型：mysql / oracle / sqlserver / postgresql</p>
 *
 * <p>识别规则：</p>
 * <ul>
 *   <li>类型：文件名关键字（mysql / ojdbc / oracle / mssql / sqljdbc / postgresql …）</li>
 *   <li>版本：第一个 {@code \d+(\.\d+){1,3}} 形式的字符串（跳过 ojdbc8 这种单数字）</li>
 * </ul>
 */
public final class DriverFileNameParser {

    /** 支持的类型简写 */
    public static final String[] ALLOWED_DB_TYPES =
            {"mysql", "oracle", "sqlserver", "postgresql"};

    /** 版本号：至少两段数字，最多四段（如 8.0 / 8.0.33 / 19.3.0.0） */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+){1,3})");

    private DriverFileNameParser() {}

    // ==================== 类型识别 ====================

    public static String detectDbType(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase();
        if (lower.contains("mysql")) return "mysql";
        if (lower.contains("ojdbc") || lower.contains("oracle")) return "oracle";
        if (lower.contains("postgresql") || lower.contains("postgres")
                || lower.contains("-pg-") || lower.endsWith("-pg.jar")) return "postgresql";
        if (lower.contains("mssql") || lower.contains("sqljdbc") || lower.contains("sqlserver")) {
            return "sqlserver";
        }
        return null;
    }

    // ==================== 版本识别 ====================

    public static String detectVersion(String fileName) {
        if (fileName == null) return null;
        Matcher m = VERSION_PATTERN.matcher(fileName);
        return m.find() ? m.group(1) : null;
    }

    // ==================== 校验与归一化 ====================

    public static boolean isAllowedDbType(String dbType) {
        if (dbType == null) return false;
        String t = dbType.trim().toLowerCase();
        for (String allowed : ALLOWED_DB_TYPES) {
            if (allowed.equals(t)) return true;
        }
        return false;
    }

    public static String normalizeDbType(String dbType) {
        if (dbType == null) return null;
        String t = dbType.trim().toLowerCase();
        if ("pg".equals(t)) return "postgresql";
        return t;
    }

    // ==================== 存储文件名 ====================

    /**
     * 构造存储文件名：{dbType}_{version}_{原始文件名}.jar
     * version 中的 '_' 会被替换为 '.'（因为 '_' 是分隔符）。
     */
    public static String buildStorageName(String dbType, String version, String originalFileName) {
        String type = normalizeDbType(dbType);
        if (!isAllowedDbType(type)) {
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType
                    + "（仅支持 " + String.join("/", ALLOWED_DB_TYPES) + "）");
        }
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("驱动版本不能为空");
        }
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("原始文件名不能为空");
        }
        String cleanVersion = version.trim().replace('_', '.');
        String cleanFile = originalFileName.trim();
        if (!cleanFile.toLowerCase().endsWith(".jar")) cleanFile = cleanFile + ".jar";
        return type + "_" + cleanVersion + "_" + cleanFile;
    }
}