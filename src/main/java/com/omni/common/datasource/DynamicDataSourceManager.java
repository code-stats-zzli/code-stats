package com.omni.common.datasource;

import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.common.datasource.type.DataSourceType;
import com.omni.common.datasource.type.impl.MysqlDataSourceType;
import com.omni.framework.pool.SimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicDataSourceManager {
    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceManager.class);
    private static final Map<String, SimpleDataSource> dataSourcePool = new ConcurrentHashMap<>();  // key 改为 String
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
}