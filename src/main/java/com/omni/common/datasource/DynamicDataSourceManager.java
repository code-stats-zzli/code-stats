package com.omni.common.datasource;

import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.common.datasource.type.DataSourceType;
import com.omni.common.datasource.type.impl.MysqlDataSourceType;
import com.omni.common.datasource.type.impl.OracleDataSourceType;
import com.omni.common.datasource.type.impl.PostgresqlDataSourceType;
import com.omni.common.util.DriverLoader;
import com.omni.framework.pool.SimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicDataSourceManager {
    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceManager.class);
    private static final Map<String, SimpleDataSource> dataSourcePool = new ConcurrentHashMap<>();
    private static final Map<String, DataSourceType> typeRegistry = new ConcurrentHashMap<>();

    static {
        // 注册三种数据库类型
        registerType("mysql", new MysqlDataSourceType());
        registerType("oracle", new OracleDataSourceType());
        registerType("postgresql", new PostgresqlDataSourceType());
        registerType("pg", new PostgresqlDataSourceType());
    }

    public static void registerType(String dbType, DataSourceType type) {
        typeRegistry.put(dbType.toLowerCase(), type);
    }

    public static SimpleDataSource getOrCreateDataSource(DataSourceConfig config) {
        return dataSourcePool.computeIfAbsent(config.getId(), id -> {
            // ---------- ① 加载驱动 ----------
            // DriverLoader 内部顺序：driver_jar 表 → resources/drivers 目录 → classpath（最后兜底）
            try {
                Driver driver = DriverLoader.getDriver(config.getDbType(), config.getVersion());
                DriverManager.registerDriver(new DriverProxy(driver));
                log.info("驱动加载成功: {} -> {}", config.getDbType(), driver.getClass().getName());
            } catch (Exception e) {
                throw new RuntimeException("加载数据库驱动失败: " + config.getDbType()
                        + " " + config.getVersion(), e);
            }

            // ---------- ② 获取 DataSourceType ----------
            DataSourceType type = typeRegistry.get(config.getDbType().toLowerCase());
            if (type == null) {
                throw new RuntimeException("不支持的数据库类型: " + config.getDbType());
            }

            // ---------- ③ 二次 classpath 检查（仅当用户显式配置了 driverClass） ----------
            // 驱动可能通过 ByteArrayClassLoader / URLClassLoader 动态加载，不在 classpath；
            // 只有当用户显式配置了 driverClass 时才检查，且失败仅 warn 不抛异常。
            String driverClass = config.getDriverClass();
            if (driverClass != null && !driverClass.trim().isEmpty()) {
                try {
                    Class.forName(driverClass);
                } catch (ClassNotFoundException e) {
                    log.warn("用户配置的 driverClass={} 不在 classpath，"
                            + "但驱动已通过 DriverLoader 动态加载，忽略此告警", driverClass);
                }
            }

            // ---------- ④ 构建数据源 ----------
            String url = type.getJdbcUrl(config);
            return new SimpleDataSource.Builder()
                    .url(url)
                    .username(config.getUsername())
                    .password(config.getPassword())
                    .maxActive(10)
                    .maxIdle(5)
                    .minIdle(2)
                    .validationQuery("SELECT 1")
                    .build();
        });
    }

    public static Connection getConnection(String dataSourceId) throws SQLException {
        SimpleDataSource ds = dataSourcePool.get(dataSourceId);
        if (ds == null) throw new SQLException("数据源未加载: " + dataSourceId);
        return ds.getConnection();
    }

    public static void refreshDataSource(DataSourceConfig config) {
        dataSourcePool.remove(config.getId());
        getOrCreateDataSource(config);
        log.info("数据源刷新: {}", config.getName());
    }

    public static void removeDataSource(String id) {
        SimpleDataSource ds = dataSourcePool.remove(id);
        if (ds != null) ds.close();
    }

    private static class DriverProxy implements Driver {
        private final Driver driver;
        public DriverProxy(Driver driver) { this.driver = driver; }
        @Override public Connection connect(String url, Properties info) throws SQLException { return driver.connect(url, info); }
        @Override public boolean acceptsURL(String url) throws SQLException { return driver.acceptsURL(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException { return driver.getPropertyInfo(url, info); }
        @Override public int getMajorVersion() { return driver.getMajorVersion(); }
        @Override public int getMinorVersion() { return driver.getMinorVersion(); }
        @Override public boolean jdbcCompliant() { return driver.jdbcCompliant(); }
        @Override public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException { return driver.getParentLogger(); }
    }
}