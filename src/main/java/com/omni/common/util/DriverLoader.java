package com.omni.common.util;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import java.sql.Driver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DriverLoader {
    private static final Logger log = LoggerFactory.getLogger(DriverLoader.class);
    private static final Map<String, Driver> driverCache = new ConcurrentHashMap<>();

    public static Driver getDriver(String dbType, String version) throws Exception {
        String key = dbType + ":" + version;
        if (driverCache.containsKey(key)) {
            return driverCache.get(key);
        }

        // 从驱动表中查找匹配的驱动（根据 name 或 fileName 模糊匹配）
        DriverJar matchedDriver = findMatchedDriver(dbType, version);
        if (matchedDriver != null) {
            String driverClassName = inferDriverClassName(matchedDriver, dbType, version);
            return loadDriverFromJar(matchedDriver, driverClassName, key);
        }

        log.warn("未找到匹配驱动，使用默认 classpath 驱动");
        return loadDefaultDriver(dbType);
    }

    private static DriverJar findMatchedDriver(String dbType, String version) {
        try {
            JdbcTemplate jt = new JdbcTemplate();
            String sql = "SELECT * FROM driver_jar WHERE name LIKE ? OR file_name LIKE ?";
            String likePattern = "%" + dbType + "%" + (version != null ? version : "");
            List<DriverJar> drivers = jt.queryForList(sql, DriverJar.class, likePattern, likePattern);
            if (drivers.isEmpty()) {
                // 尝试只用 dbType
                likePattern = "%" + dbType + "%";
                drivers = jt.queryForList(sql, DriverJar.class, likePattern, likePattern);
            }
            if (!drivers.isEmpty()) {
                return drivers.get(0);
            }
        } catch (Exception e) {
            log.debug("查询驱动失败", e);
        }
        return null;
    }

    private static String inferDriverClassName(DriverJar driver, String dbType, String version) {
        // 根据文件名或驱动名称推断驱动类名，也可映射到固定规则
        String fileName = driver.getFileName().toLowerCase();
        if (fileName.contains("mysql")) {
            if (version != null && version.startsWith("5")) return "com.mysql.jdbc.Driver";
            else return "com.mysql.cj.jdbc.Driver";
        } else if (fileName.contains("postgresql")) {
            return "org.postgresql.Driver";
        } else if (fileName.contains("ojdbc")) {
            return "oracle.jdbc.driver.OracleDriver";
        }
        // 默认尝试常见类名（可能失败）
        return "com.mysql.cj.jdbc.Driver";
    }

    private static Driver loadDriverFromJar(DriverJar driverJar, String driverClassName, String cacheKey) throws Exception {
        ByteArrayClassLoader classLoader = new ByteArrayClassLoader(driverJar.getJarData(), Thread.currentThread().getContextClassLoader());
        Class<?> clazz = classLoader.loadClass(driverClassName);
        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        driverCache.put(cacheKey, driver);
        log.info("成功加载驱动: {} ({}), 类名: {}", driverJar.getName(), driverJar.getId(), driverClassName);
        return driver;
    }

    private static Driver loadDefaultDriver(String dbType) throws Exception {
        String driverClassName;
        switch (dbType.toLowerCase()) {
            case "mysql":
                try {
                    driverClassName = "com.mysql.cj.jdbc.Driver";
                    return (Driver) Class.forName(driverClassName).getDeclaredConstructor().newInstance();
                } catch (ClassNotFoundException e) {
                    driverClassName = "com.mysql.jdbc.Driver";
                    return (Driver) Class.forName(driverClassName).getDeclaredConstructor().newInstance();
                }
            case "oracle":
                driverClassName = "oracle.jdbc.driver.OracleDriver";
                return (Driver) Class.forName(driverClassName).getDeclaredConstructor().newInstance();
            case "postgresql":
                driverClassName = "org.postgresql.Driver";
                return (Driver) Class.forName(driverClassName).getDeclaredConstructor().newInstance();
            default:
                throw new RuntimeException("不支持的数据库类型: " + dbType);
        }
    }
}