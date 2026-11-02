package com.omni.common.util;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * 智能驱动类名解析器。
 *
 * 解析顺序（匹配成功即返回）：
 *   1. 文件名称解析（mysql-connector-java / ojdbc / postgresql 关键字）
 *   2. 目录扫描（从 jar 内的包路径推断）
 *   3. 数据库类型默认（dbType → 标准驱动类名）
 *   4. 全部失败返回 null（由调用方走 classpath 兜底）
 */
public final class DriverClassNameResolver {
    private static final Logger log = LoggerFactory.getLogger(DriverClassNameResolver.class);

    private DriverClassNameResolver() {}

    /**
     * 综合智能解析：依次尝试 文件名 → 目录扫描 → 数据库类型
     */
    public static String resolve(DriverJar jar) {
        if (jar == null) return null;
        // 1. 文件名
        String cls = fromFileName(jar.getFileName());
        if (cls != null) {
            log.debug("从文件名解析驱动类名: {} -> {}", jar.getFileName(), cls);
            return cls;
        }
        // 2. 目录扫描
        cls = fromJarDirectory(jar.getJarData());
        if (cls != null) {
            log.debug("从 jar 目录解析驱动类名: {} -> {}", jar.getFileName(), cls);
            return cls;
        }
        // 3. 数据库类型
        cls = fromDbType(jar.getDbType());
        if (cls != null) {
            log.debug("从数据库类型解析驱动类名: {} -> {}", jar.getDbType(), cls);
        }
        return cls;
    }

    /** 1. 文件名关键字匹配 */
    public static String fromFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return null;
        String lower = fileName.toLowerCase();

        if (lower.contains("mysql-connector-java-5.") || lower.contains("mysql-connector-java-3.")) {
            return "com.mysql.jdbc.Driver";
        }
        if (lower.contains("mysql-connector-java") || lower.contains("mysql-connector-j")
                || lower.contains("mysql-jdbc")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (lower.contains("ojdbc")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (lower.contains("postgresql") || lower.contains("postgres")) {
            return "org.postgresql.Driver";
        }
        if (lower.contains("mssql-jdbc") || lower.contains("sqljdbc")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        return null;
    }

    /** 2. jar 内包路径扫描 */
    public static String fromJarDirectory(byte[] jarData) {
        if (jarData == null || jarData.length == 0) return null;
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarData))) {
            Set<String> prefixes = new HashSet<>();
            JarEntry entry;
            int count = 0;
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                int idx = name.lastIndexOf('/');
                if (idx <= 0) continue;
                String pkgPath = name.substring(0, idx);
                prefixes.add(pkgPath);
                // 收集前几层
                String[] parts = pkgPath.split("/");
                for (int i = 1; i <= Math.min(parts.length, 4); i++) {
                    prefixes.add(String.join("/", Arrays.copyOfRange(parts, 0, i)));
                }
                if (++count > 1200) break;
            }
            if (prefixes.contains("com/mysql/cj/jdbc")) return "com.mysql.cj.jdbc.Driver";
            if (prefixes.contains("com/mysql/jdbc"))    return "com.mysql.jdbc.Driver";
            if (prefixes.contains("oracle/jdbc") || prefixes.contains("oracle/jdbc/driver"))
                return "oracle.jdbc.OracleDriver";
            if (prefixes.contains("org/postgresql"))   return "org.postgresql.Driver";
            if (prefixes.contains("com/microsoft/sqlserver/jdbc"))
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } catch (Exception e) {
            log.warn("jar 目录扫描失败: {}", e.getMessage());
        }
        return null;
    }

    /** 3. 数据库类型默认 */
    public static String fromDbType(String dbType) {
        if (dbType == null) return null;
        switch (dbType.trim().toLowerCase()) {
            case "mysql":       return "com.mysql.cj.jdbc.Driver";
            case "oracle":      return "oracle.jdbc.OracleDriver";
            case "postgresql":
            case "pg":          return "org.postgresql.Driver";
            case "sqlserver":   return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default:            return null;
        }
    }
}