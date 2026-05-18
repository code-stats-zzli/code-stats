package com.omni.framework.jdbc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.pool.SimpleDataSource;
import com.omni.framework.spring.config.ConfigLoader;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DataSourceUtils {
    private static final Logger log = LoggerFactory.getLogger(DataSourceUtils.class);
    private static final SimpleDataSource dataSource;
    private static volatile DbType dbType;       // 数据库类型，从URL解析

    // 数据库类型枚举，便于扩展
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

        // 解析数据库类型
        dbType = detectDbType(url);
        // 兼容低版本：显式加载驱动类
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
                // 兼容 MySQL 5.x 和 8.x 驱动，优先使用 8.x 的类名
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
                // 不主动加载，依赖 DriverManager 自动发现
                break;
        }
    }

    private static void initTable() {
        // 通用建表语句（MySQL和Oracle语法兼容）
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
        } else { // MySQL 默认
            createTableSql = "CREATE TABLE IF NOT EXISTS `user` (" +
                    "id INT PRIMARY KEY, " +
                    "name VARCHAR(100), " +
                    "age INT)";
            checkExistsSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'user'";
            insertDataSql = "INSERT INTO `user` (id, name, age) VALUES (1, 'Jerry', 25) ON DUPLICATE KEY UPDATE name = VALUES(name), age = VALUES(age)";
            updateDataSql = null; // MySQL 使用 ON DUPLICATE KEY 已经覆盖插入更新
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // 创建表
            if (dbType == DbType.ORACLE) {
                // Oracle 需要先检查表是否存在
                try (ResultSet rs = stmt.executeQuery(checkExistsSql)) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        stmt.execute(createTableSql);
                    }
                }
            } else {
                stmt.execute(createTableSql); // MySQL 的 IF NOT EXISTS 生效
            }

            // 插入或更新数据
            if (dbType == DbType.ORACLE) {
                // 检查记录是否存在
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
}