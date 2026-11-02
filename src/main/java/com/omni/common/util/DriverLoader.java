package com.omni.common.util;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.sql.Driver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 驱动加载器。
 *
 * 加载流程：
 *   1. 从 driver_jar 表匹配 jar（按 dbType + version）
 *   2. 用 DriverClassNameResolver 智能识别类名
 *      优先文件名 → 目录扫描 → 数据库类型默认
 *   3. classpath 兜底
 */
public class DriverLoader {
    private static final Logger log = LoggerFactory.getLogger(DriverLoader.class);
    private static final Map<String, Driver> driverCache = new ConcurrentHashMap<>();

    public static Driver getDriver(String dbType, String version) throws Exception {
        String key = dbType + ":" + (version == null ? "" : version);
        Driver cached = driverCache.get(key);
        if (cached != null) return cached;

        // 1. 从 driver_jar 表查询
        DriverJar matched = findMatchedDriver(dbType, version);
        if (matched != null) {
            String className = DriverClassNameResolver.resolve(matched);
            if (className == null) {
                throw new RuntimeException("无法从驱动包确定类名: " + matched.getFileName()
                        + " (dbType=" + dbType + ")");
            }
            return loadDriverFromJar(matched, className, key);
        }

        // 2. classpath 兜底
        log.warn("driver_jar 未找到匹配驱动，使用 classpath 兜底: dbType={}", dbType);
        return loadDefaultDriver(dbType);
    }

    private static DriverJar findMatchedDriver(String dbType, String version) {
        try {
            JdbcTemplate jt = new JdbcTemplate();
            // 优先精确匹配 dbType + version
            if (version != null && !version.trim().isEmpty()) {
                List<DriverJar> exact = jt.queryForList(
                        "SELECT * FROM driver_jar WHERE LOWER(db_type)=LOWER(?) AND LOWER(version)=LOWER(?) LIMIT 1",
                        DriverJar.class, dbType, version);
                if (!exact.isEmpty()) return exact.get(0);
            }
            // 回退：只按 dbType 匹配
            List<DriverJar> byType = jt.queryForList(
                    "SELECT * FROM driver_jar WHERE LOWER(db_type)=LOWER(?) ORDER BY create_time DESC LIMIT 1",
                    DriverJar.class, dbType);
            if (!byType.isEmpty()) return byType.get(0);
        } catch (Exception e) {
            log.debug("查询 driver_jar 失败", e);
        }
        return null;
    }

    private static Driver loadDriverFromJar(DriverJar driverJar, String driverClassName, String cacheKey) throws Exception {
        ByteArrayClassLoader classLoader = new ByteArrayClassLoader(
                driverJar.getJarData(), Thread.currentThread().getContextClassLoader());
        Class<?> clazz = classLoader.loadClass(driverClassName);
        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        driverCache.put(cacheKey, driver);
        log.info("成功加载驱动: {} ({}), 类名: {}",
                driverJar.getName(), driverJar.getId(), driverClassName);
        return driver;
    }

    private static Driver loadDefaultDriver(String dbType) throws Exception {
        String driverClassName;
        switch (dbType.toLowerCase()) {
            case "mysql":
                try {
                    return (Driver) Class.forName("com.mysql.cj.jdbc.Driver")
                            .getDeclaredConstructor().newInstance();
                } catch (ClassNotFoundException e) {
                    return (Driver) Class.forName("com.mysql.jdbc.Driver")
                            .getDeclaredConstructor().newInstance();
                }
            case "oracle":
                driverClassName = "oracle.jdbc.OracleDriver";
                return (Driver) Class.forName(driverClassName)
                        .getDeclaredConstructor().newInstance();
            case "postgresql":
            case "pg":
                driverClassName = "org.postgresql.Driver";
                return (Driver) Class.forName(driverClassName)
                        .getDeclaredConstructor().newInstance();
            case "sqlserver":
                driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                return (Driver) Class.forName(driverClassName)
                        .getDeclaredConstructor().newInstance();
            default:
                throw new RuntimeException("不支持的数据库类型: " + dbType);
        }
    }
}