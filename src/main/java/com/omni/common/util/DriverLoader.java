package com.omni.common.util;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.sql.Driver;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DriverLoader {
    private static final Logger log = LoggerFactory.getLogger(DriverLoader.class);
    private static final Map<String, Driver> driverCache = new ConcurrentHashMap<>();
    private static Properties mappingProps = new Properties();

    static {
        try (var is = DriverLoader.class.getClassLoader().getResourceAsStream("driver-mapping.properties")) {
            if (is != null) mappingProps.load(is);
        } catch (Exception e) {
            log.warn("加载驱动映射文件失败", e);
        }
    }

    public static Driver getDriver(String dbType, String version, String driverCode) throws Exception {
        String key = dbType + ":" + version + ":" + (driverCode != null ? driverCode : "default");
        if (driverCache.containsKey(key)) {
            return driverCache.get(key);
        }

        DriverJar driverJar = null;
        if (driverCode != null && !driverCode.isEmpty()) {
            driverJar = findDriverByCode(driverCode);
        }

        if (driverJar == null) {
            String propKey = dbType + "." + version;
            String code = mappingProps.getProperty(propKey);
            if (code != null && !code.isEmpty()) {
                // 格式可能是 "编码" 或 "编码|驱动类名"
                String[] parts = code.split("\\|");
                String resolvedCode = parts[0];
                driverJar = findDriverByCode(resolvedCode);
                if (driverJar != null && parts.length > 1) {
                    // 驱动类名从映射文件获取
                    String driverClassName = parts[1];
                    return loadDriverFromJar(driverJar, driverClassName, key);
                }
            }
        }

        if (driverJar == null) {
            log.warn("未找到匹配驱动，使用默认 classpath 驱动");
            return loadDefaultDriver(dbType);
        }

        // 需要知道驱动类名：从映射文件获取或通过固定规则推断
        String driverClassName = getDriverClassNameFromMapping(dbType, version, driverJar.getCode());
        if (driverClassName == null) {
            throw new RuntimeException("无法确定驱动类名，请在 driver-mapping.properties 中配置完整格式: 类型.版本=驱动编码|驱动类名");
        }
        return loadDriverFromJar(driverJar, driverClassName, key);
    }

    private static Driver loadDriverFromJar(DriverJar driverJar, String driverClassName, String cacheKey) throws Exception {
        ByteArrayClassLoader classLoader = new ByteArrayClassLoader(driverJar.getJarData(), Thread.currentThread().getContextClassLoader());
        Class<?> clazz = classLoader.loadClass(driverClassName);
        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        driverCache.put(cacheKey, driver);
        log.info("成功加载驱动: {} ({}), 类名: {}", driverJar.getName(), driverJar.getCode(), driverClassName);
        return driver;
    }

    private static String getDriverClassNameFromMapping(String dbType, String version, String driverCode) {
        String propKey = dbType + "." + version;
        String value = mappingProps.getProperty(propKey);
        if (value != null && value.contains("|")) {
            String[] parts = value.split("\\|");
            if (parts.length >= 2 && parts[0].equals(driverCode)) {
                return parts[1];
            }
        }
        // 降级：根据驱动编码推测常见类名
        if (driverCode.contains("mysql")) return "com.mysql.cj.jdbc.Driver";
        if (driverCode.contains("ojdbc")) return "oracle.jdbc.driver.OracleDriver";
        if (driverCode.contains("postgresql")) return "org.postgresql.Driver";
        return null;
    }

    private static DriverJar findDriverByCode(String code) {
        try {
            JdbcTemplate jt = new JdbcTemplate();
            return jt.queryForObject("SELECT * FROM driver_jar WHERE code = ?", DriverJar.class, code);
        } catch (Exception e) {
            log.debug("查询驱动失败: {}", code, e);
            return null;
        }
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