package com.omni.common.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 安全工具类：路径遍历防护、SQL 注入防护（表名/列名白名单）
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    /**
     * 校验路径是否在根目录下（防止路径遍历）
     * @param root 根目录
     * @param userPath 用户输入路径
     * @return 规范化后的绝对路径
     * @throws SecurityException 如果路径越界
     */
    public static Path resolveSafePath(Path root, String userPath) throws SecurityException {
        Path target;
        if (userPath == null || userPath.trim().isEmpty()) {
            target = root;
        } else {
            target = root.resolve(userPath).normalize();
        }
        if (!target.startsWith(root)) {
            throw new SecurityException("Access denied: path traversal attempt");
        }
        return target;
    }

    /**
     * 验证表名是否合法（仅包含字母数字下划线，防止 SQL 注入）
     */
    public static boolean isValidTableName(String name) {
        return name != null && Pattern.matches("^[a-zA-Z][a-zA-Z0-9_]*$", name);
    }

    /**
     * 验证列名是否合法
     */
    public static boolean isValidColumnName(String name) {
        return name != null && Pattern.matches("^[a-zA-Z][a-zA-Z0-9_]*$", name);
    }
}