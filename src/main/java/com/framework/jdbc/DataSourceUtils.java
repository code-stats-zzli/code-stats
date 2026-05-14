package com.framework.jdbc;

import com.framework.pool.SimpleDataSource;
import com.framework.spring.config.ConfigLoader;
import com.framework.log.LoggerFactory;
import com.framework.log.core.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DataSourceUtils {
    private static final Logger log = LoggerFactory.getLogger(DataSourceUtils.class);
    private static final SimpleDataSource dataSource;

    static {
        String url = ConfigLoader.getProperty("db.url");
        String username = ConfigLoader.getProperty("db.username");
        String password = ConfigLoader.getProperty("db.password");
        int maxActive = Integer.parseInt(ConfigLoader.getProperty("db.maxActive", "10"));
        int maxIdle = Integer.parseInt(ConfigLoader.getProperty("db.maxIdle", "5"));
        int minIdle = Integer.parseInt(ConfigLoader.getProperty("db.minIdle", "2"));
        long maxWaitMillis = Long.parseLong(ConfigLoader.getProperty("db.maxWaitMillis", "30000"));
        String validationQuery = ConfigLoader.getProperty("db.validationQuery", "SELECT 1");

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

    private static void initTable() {
        String createTable = "CREATE TABLE IF NOT EXISTS user (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(100), " +
                "age INT)";
        String insertData = "INSERT INTO user (id, name, age) VALUES " +
                "(1, 'Jerry', 25), (2, 'Tom', 30) " +
                "ON DUPLICATE KEY UPDATE name=VALUES(name), age=VALUES(age)";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTable);
            stmt.execute(insertData);
            log.info("MySQL table 'user' initialized via custom connection pool.");
        } catch (SQLException e) {
            log.error("初始化 user 表失败", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void closePool() {
        dataSource.close();
    }
}