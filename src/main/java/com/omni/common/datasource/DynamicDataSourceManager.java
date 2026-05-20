package com.omni.common.datasource;

import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.common.datasource.type.DataSourceType;
import com.omni.common.datasource.type.impl.MysqlDataSourceType;
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
        registerType("mysql", new MysqlDataSourceType());
        // 可扩展 oracle, postgresql 等
    }

    public static void registerType(String dbType, DataSourceType type) {
        typeRegistry.put(dbType.toLowerCase(), type);
    }

    public static SimpleDataSource getOrCreateDataSource(DataSourceConfig config) {
        return dataSourcePool.computeIfAbsent(config.getId(), id -> {
            // 1. 使用 DriverLoader 加载驱动并注册
            try {
                Driver driver = DriverLoader.getDriver(config.getDbType(), config.getVersion(), config.getDriverCode());
                // 注册驱动（如果 DriverManager 尚未注册）
                // 注意：每个驱动实例只需要注册一次，这里简单注册，避免重复注册可做缓存
                DriverManager.registerDriver(new DriverProxy(driver));
                log.info("驱动加载成功: {} -> {}", config.getDbType(), driver.getClass().getName());
            } catch (Exception e) {
                throw new RuntimeException("加载数据库驱动失败: " + config.getDbType() + " " + config.getVersion(), e);
            }

            DataSourceType type = typeRegistry.get(config.getDbType().toLowerCase());
            if (type == null) {
                throw new RuntimeException("不支持的数据库类型: " + config.getDbType());
            }
            String driverClass = type.getDriverClass(config);
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("驱动类未找到: " + driverClass, e);
            }
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
        if (ds == null) {
            throw new SQLException("数据源未加载: " + dataSourceId);
        }
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

    /**
     * 驱动代理类，解决动态加载的驱动可能没有正确注册到 DriverManager 的问题
     */
    private static class DriverProxy implements Driver {
        private final Driver driver;

        public DriverProxy(Driver driver) {
            this.driver = driver;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return driver.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return driver.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return driver.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return driver.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return driver.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return driver.getParentLogger();
        }
    }
}