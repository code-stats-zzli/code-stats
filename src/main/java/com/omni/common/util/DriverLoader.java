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
 * <p>加载顺序（命中即返回）：</p>
 *   <ol>
 *     <li><b>driver_jar 表</b>（数据库 blob，dbType+version 精确；无 version 取该 dbType 最新）</li>
 *     <li><b>classpath</b> 内置驱动</li>
 *     <li><b>resources/drivers 目录</b>（文件名 {dbType}_{version}_{fileName}.jar）</li>
 *   </ol>
 */
public class DriverLoader {
    private static final Logger log = LoggerFactory.getLogger(DriverLoader.class);
    private static final Map<String, Driver> driverCache = new ConcurrentHashMap<>();

    public static Driver getDriver(String dbType, String version) throws Exception {
        String type = DriverFileNameParser.normalizeDbType(dbType);
        if (!DriverFileNameParser.isAllowedDbType(type)) {
            throw new RuntimeException("不支持的数据库类型: " + dbType
                    + "（仅支持 " + String.join("/", DriverFileNameParser.ALLOWED_DB_TYPES) + "）");
        }
        String key = type + ":" + (version == null ? "" : version);
        Driver cached = driverCache.get(key);
        if (cached != null) return cached;

        // ---------- ① driver_jar 表 ----------
        DriverJar matched = findMatchedDriver(type, version);
        if (matched != null) {
            String className = DriverClassNameResolver.resolve(matched);
            if (className == null) {
                throw new RuntimeException("无法从驱动包确定类名: " + matched.getFileName()
                        + " (dbType=" + type + ", version=" + matched.getVersion() + ")");
            }
            return loadDriverFromJar(matched, className, key);
        }

        // ---------- ② classpath 内置驱动 ----------
        try {
            Driver d = loadDefaultDriver(type);
            driverCache.put(key, d);
            log.info("classpath 加载驱动成功: dbType={}, class={}", type, d.getClass().getName());
            return d;
        } catch (ClassNotFoundException e) {
            log.info("classpath 未找到驱动: dbType={}, 继续尝试资源目录", type);
        }

        // ---------- ③ resources/drivers 目录 ----------
        Driver dirDriver = DriverResourceScanner.loadDriver(type, version);
        if (dirDriver != null) {
            driverCache.put(key, dirDriver);
            return dirDriver;
        }

        throw new RuntimeException("无法加载数据库驱动: dbType=" + type
                + ", version=" + (version == null ? "null" : version)
                + "（driver_jar 表无匹配，classpath 无该类，资源目录 "
                + DriverResourceScanner.getDriversDir() + " 也无匹配 jar）");
    }

    private static DriverJar findMatchedDriver(String dbType, String version) {
        try {
            JdbcTemplate jt = new JdbcTemplate();
            if (version != null && !version.trim().isEmpty()) {
                List<DriverJar> exact = jt.queryForList(
                        "SELECT * FROM driver_jar " +
                                "WHERE LOWER(db_type)=LOWER(?) AND LOWER(version)=LOWER(?) LIMIT 1",
                        DriverJar.class, dbType, version);
                if (!exact.isEmpty()) return exact.get(0);
            }
            List<DriverJar> byType = jt.queryForList(
                    "SELECT * FROM driver_jar WHERE LOWER(db_type)=LOWER(?) " +
                            "ORDER BY create_time DESC LIMIT 1",
                    DriverJar.class, dbType);
            if (!byType.isEmpty()) return byType.get(0);
        } catch (Exception e) {
            log.debug("查询 driver_jar 失败", e);
        }
        return null;
    }

    private static Driver loadDriverFromJar(DriverJar driverJar, String driverClassName, String cacheKey)
            throws Exception {
        ByteArrayClassLoader classLoader = new ByteArrayClassLoader(
                driverJar.getJarData(), Thread.currentThread().getContextClassLoader());
        Class<?> clazz = classLoader.loadClass(driverClassName);
        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        driverCache.put(cacheKey, driver);
        log.info("driver_jar 加载驱动成功: {} ({}), 类名: {}",
                driverJar.getName(), driverJar.getId(), driverClassName);
        return driver;
    }

    private static Driver loadDefaultDriver(String dbType) throws Exception {
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
                return (Driver) Class.forName("oracle.jdbc.OracleDriver")
                        .getDeclaredConstructor().newInstance();
            case "postgresql":
            case "pg":
                return (Driver) Class.forName("org.postgresql.Driver")
                        .getDeclaredConstructor().newInstance();
            case "sqlserver":
                return (Driver) Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                        .getDeclaredConstructor().newInstance();
            default:
                throw new RuntimeException("不支持的数据库类型: " + dbType);
        }
    }

    public static void clearCache() {
        driverCache.clear();
        log.info("DriverLoader 缓存已清空");
    }
}