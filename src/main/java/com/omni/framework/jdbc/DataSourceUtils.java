// ======== D:\space\git\CodeStats\src\main\java\com\omni\framework\jdbc\DataSourceUtils.java ========
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
    private static volatile DbType dbType;

    public enum DbType {
        MYSQL, ORACLE, UNKNOWN
    }

    static {
        String url = ConfigLoader.getProperty("db.url");
        String username = ConfigLoader.getProperty("db.username");
        String password = ConfigLoader.getProperty("db.password");
        int maxActive = Integer.parseInt(ConfigLoader.getProperty("db.maxActive", "10"));
        int maxIdle = Integer.parseInt(ConfigLoader.getProperty("db.maxIdle", "5"));
        int minIdle = Integer.parseInt(ConfigLoader.getProperty("db.minIdle", "2"));
        long maxWaitMillis = Long.parseLong(ConfigLoader.getProperty("db.maxWaitMillis", "30000"));
        String validationQuery = ConfigLoader.getProperty("db.validationQuery", "SELECT 1");

        dbType = detectDbType(url);
        loadDriver();

        dataSource = new SimpleDataSource.Builder()
                .url(url)
                .username(username)
                .password(password)
                .maxActive(maxActive)
                .maxIdle(maxIdle)
                .minIdle(minIdle)
                .maxWaitMillis(maxWaitMillis)
                .validationQuery(validationQuery)
                .build();
        initTable();
    }

    private static DbType detectDbType(String url) {
        if (url == null) return DbType.UNKNOWN;
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("mysql")) {
            return DbType.MYSQL;
        } else if (lowerUrl.contains("oracle")) {
            return DbType.ORACLE;
        }
        return DbType.UNKNOWN;
    }

    private static void loadDriver() {
        String driverClassName = null;
        switch (dbType) {
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

    private static void initTable() {
        String createTableSql;
        String checkExistsSql;
        String insertDataSql;
        String updateDataSql;

        if (dbType == DbType.ORACLE) {
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

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            if (dbType == DbType.ORACLE) {
                try (ResultSet rs = stmt.executeQuery(checkExistsSql)) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        stmt.execute(createTableSql);
                    }
                }
            } else {
                stmt.execute(createTableSql);
            }

            if (dbType == DbType.ORACLE) {
                String countSql = "SELECT COUNT(*) FROM \"user\" WHERE id = 1";
                try (ResultSet rs = stmt.executeQuery(countSql)) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        stmt.execute(insertDataSql);
                    } else {
                        stmt.execute(updateDataSql);
                    }
                }
            } else {
                stmt.execute(insertDataSql);
            }
            log.info("Table 'user' initialized successfully for {}", dbType);
        } catch (SQLException e) {
            log.error("Failed to initialize user table", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void closePool() {
        dataSource.close();
    }

    public static DbType getDbType() {
        return dbType;
    }

    // ========== 新增：暴露数据源实例 ==========
    public static SimpleDataSource getDataSource() {
        return dataSource;
    }

    // ========== 新增：获取连接池统计信息 ==========
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