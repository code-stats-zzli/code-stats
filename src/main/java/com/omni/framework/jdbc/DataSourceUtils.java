package com.omni.framework.jdbc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.pool.SimpleDataSource;
import com.omni.framework.spring.config.ConfigLoader;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class DataSourceUtils {
    private static final Logger log = LoggerFactory.getLogger(DataSourceUtils.class);

    private static final SimpleDataSource dataSource;
    private static final Throwable initFailure;
    private static final DbType dbType;

    public enum DbType { MYSQL, ORACLE, UNKNOWN }

    // ★ FIX: 静态块绝不让异常逃逸。
    //   修复前：db.url 为空 / minIdle=2 建连失败 / initTable NPE 都会让类初始化失败，
    //           之后所有访问变成 NoClassDefFoundError: Could not initialize class DataSourceUtils，
    //           catch (Exception) 接不住 Error，日志全部丢失。
    //   修复后：失败只记录 initFailure，dataSource=null；
    //           后续 getConnection() 抛带根因的 SQLException，上层 catch (Exception) 能接住并打印。
    static {
        SimpleDataSource ds = null;
        Throwable failure = null;
        DbType detected = DbType.UNKNOWN;
        try {
            String url = ConfigLoader.getProperty("db.url");
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalStateException("db.url 未配置（application.properties 缺少 db.url）");
            }
            String username = ConfigLoader.getProperty("db.username");
            String password = ConfigLoader.getProperty("db.password");
            int maxActive = parseInt(ConfigLoader.getProperty("db.maxActive", "10"), 10);
            int maxIdle = parseInt(ConfigLoader.getProperty("db.maxIdle", "5"), 5);
            int minIdle = parseInt(ConfigLoader.getProperty("db.minIdle", "2"), 2);
            long maxWaitMillis = parseLong(ConfigLoader.getProperty("db.maxWaitMillis", "30000"), 30000L);
            String validationQuery = ConfigLoader.getProperty("db.validationQuery", "SELECT 1");

            detected = detectDbType(url);
            loadDriver(detected);

            ds = new SimpleDataSource.Builder()
                    .url(url.trim())
                    .username(username)
                    .password(password)
                    .maxActive(maxActive)
                    .maxIdle(maxIdle)
                    .minIdle(minIdle)
                    .maxWaitMillis(maxWaitMillis)
                    .validationQuery(validationQuery)
                    .build();

            initTable(ds, detected);
        } catch (Throwable e) {
            failure = e;
            try {
                log.error("DataSourceUtils 初始化失败: " + e, e);
            } catch (Throwable ignored) {
                // 日志失败也不能让类初始化失败
            }
        }
        dataSource = ds;
        initFailure = failure;
        dbType = detected;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    private static DbType detectDbType(String url) {
        if (url == null) return DbType.UNKNOWN;
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("mysql")) return DbType.MYSQL;
        if (lowerUrl.contains("oracle")) return DbType.ORACLE;
        return DbType.UNKNOWN;
    }

    private static void loadDriver(DbType type) {
        switch (type) {
            case MYSQL:
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (ClassNotFoundException e) {
                    try {
                        Class.forName("com.mysql.jdbc.Driver");
                    } catch (ClassNotFoundException ex) {
                        log.warn("MySQL JDBC Driver not found in classpath");
                    }
                }
                break;
            case ORACLE:
                try {
                    Class.forName("oracle.jdbc.driver.OracleDriver");
                } catch (ClassNotFoundException e) {
                    log.warn("Oracle JDBC Driver not found in classpath");
                }
                break;
            default:
                break;
        }
    }

    // ★ FIX: 改为传参（原实现里 initTable() 通过静态 getConnection() 读 dataSource，
    //         如果 dataSource 构造失败就会 NPE；传参后语义明确，也不再依赖字段可见性）。
    private static void initTable(SimpleDataSource ds, DbType type) {
        if (ds == null) return;
        String createTableSql;
        String checkExistsSql;
        String insertDataSql;
        String updateDataSql;

        if (type == DbType.ORACLE) {
            createTableSql = "CREATE TABLE \"user\" (" +
                    "id NUMBER(10) PRIMARY KEY, " +
                    "name VARCHAR2(100), " +
                    "age NUMBER(3))";
            checkExistsSql = "SELECT COUNT(*) FROM user_tables WHERE table_name = 'USER'";
            insertDataSql = "INSERT INTO \"user\" (id, name, age) VALUES (1, 'Jerry', 25)";
            updateDataSql = "UPDATE \"user\" SET name = 'Jerry', age = 25 WHERE id = 1";
        } else {
            createTableSql = "CREATE TABLE IF NOT EXISTS `user` (" +
                    "id INT PRIMARY KEY, " +
                    "name VARCHAR(100), " +
                    "age INT)";
            checkExistsSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'user'";
            insertDataSql = "INSERT INTO `user` (id, name, age) VALUES (1, 'Jerry', 25) ON DUPLICATE KEY UPDATE name = VALUES(name), age = VALUES(age)";
            updateDataSql = null;
        }

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            if (type == DbType.ORACLE) {
                try (ResultSet rs = stmt.executeQuery(checkExistsSql)) {
                    if (rs.next() && rs.getInt(1) == 0) stmt.execute(createTableSql);
                }
            } else {
                stmt.execute(createTableSql);
            }

            if (type == DbType.ORACLE) {
                String countSql = "SELECT COUNT(*) FROM \"user\" WHERE id = 1";
                try (ResultSet rs = stmt.executeQuery(countSql)) {
                    if (rs.next() && rs.getInt(1) == 0) stmt.execute(insertDataSql);
                    else stmt.execute(updateDataSql);
                }
            } else {
                stmt.execute(insertDataSql);
            }
            log.info("Table 'user' initialized successfully for {}", type);
        } catch (Throwable e) {
            log.warn("Failed to initialize user table", e);
        }
    }

    // ★ FIX: dataSource 为 null 时抛明确 SQLException（带根因），
    //         上层 catch (Exception) 能接住并打印日志。
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            String msg = "数据源未初始化（DataSourceUtils 静态初始化失败）";
            if (initFailure != null) msg += "：" + initFailure;
            throw new SQLException(msg, initFailure);
        }
        return dataSource.getConnection();
    }

    // ★ FIX: null-safe
    public static void closePool() {
        if (dataSource != null) {
            try { dataSource.close(); } catch (Throwable e) { log.warn("close pool failed", e); }
        }
    }

    public static DbType getDbType() { return dbType; }
    public static SimpleDataSource getDataSource() { return dataSource; }

    public static Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        if (dataSource != null) {
            stats.put("active", dataSource.getActiveCount());
            stats.put("idle", dataSource.getIdleCount());
            stats.put("max", dataSource.getMaxActive());
        } else {
            stats.put("active", 0);
            stats.put("idle", 0);
            stats.put("max", 0);
        }
        return stats;
    }
}