// ======== D:\space\git\CodeStats\src\main\java\com\omni\framework\pool\SimpleDataSource.java ========
package com.omni.framework.pool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleDataSource {
    private final String url;
    private final String username;
    private final String password;
    private final int maxActive;
    private final int maxIdle;
    private final int minIdle;
    private final long maxWaitMillis;
    private final String validationQuery;

    private final BlockingQueue<Connection> idleConnections;
    private final Semaphore semaphore;          // 控制最大活跃连接数
    private final AtomicInteger activeCount = new AtomicInteger(0);

    private volatile boolean closed = false;

    private SimpleDataSource(Builder builder) {
        this.url = builder.url;
        this.username = builder.username;
        this.password = builder.password;
        this.maxActive = builder.maxActive;
        this.maxIdle = builder.maxIdle;
        this.minIdle = builder.minIdle;
        this.maxWaitMillis = builder.maxWaitMillis;
        this.validationQuery = builder.validationQuery;
        this.semaphore = new Semaphore(maxActive);

        // 初始化 minIdle 个连接
        idleConnections = new ArrayBlockingQueue<>(maxIdle);
        for (int i = 0; i < minIdle; i++) {
            try {
                Connection conn = createNewConnection();
                idleConnections.offer(conn);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create initial connection", e);
            }
        }
    }

    private Connection createNewConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("DataSource is closed");
        }
        boolean acquired;
        try {
            if (maxWaitMillis > 0) {
                acquired = semaphore.tryAcquire(maxWaitMillis, TimeUnit.MILLISECONDS);
            } else {
                semaphore.acquire();
                acquired = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }
        if (!acquired) {
            throw new SQLException("Timeout waiting for connection");
        }

        Connection conn = idleConnections.poll();
        if (conn == null) {
            try {
                conn = createNewConnection();
            } catch (SQLException e) {
                semaphore.release();
                throw e;
            }
        }
        activeCount.incrementAndGet();
        if (!isValid(conn)) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
            return getConnection();
        }
        return new PooledConnection(conn, this);
    }

    void returnConnection(Connection conn) {
        if (closed) {
            try { conn.close(); } catch (SQLException ignored) {}
            return;
        }
        activeCount.decrementAndGet();
        if (idleConnections.size() < maxIdle) {
            if (isValid(conn)) {
                idleConnections.offer(conn);
            } else {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        } else {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        semaphore.release();
    }

    private boolean isValid(Connection conn) {
        if (validationQuery == null || validationQuery.isEmpty()) {
            return true;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(validationQuery);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public void close() {
        closed = true;
        for (Connection conn : idleConnections) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        idleConnections.clear();
    }

    // ========== 新增：对外暴露连接池状态 ==========
    public int getActiveCount() {
        return activeCount.get();
    }

    public int getIdleCount() {
        return idleConnections.size();
    }

    public int getMaxActive() {
        return maxActive;
    }

    // Builder 模式
    public static class Builder {
        private String url;
        private String username;
        private String password;
        private int maxActive = 10;
        private int maxIdle = 5;
        private int minIdle = 2;
        private long maxWaitMillis = 30000;
        private String validationQuery = "SELECT 1";

        public Builder url(String url) { this.url = url; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder maxActive(int maxActive) { this.maxActive = maxActive; return this; }
        public Builder maxIdle(int maxIdle) { this.maxIdle = maxIdle; return this; }
        public Builder minIdle(int minIdle) { this.minIdle = minIdle; return this; }
        public Builder maxWaitMillis(long maxWaitMillis) { this.maxWaitMillis = maxWaitMillis; return this; }
        public Builder validationQuery(String validationQuery) { this.validationQuery = validationQuery; return this; }

        public SimpleDataSource build() {
            if (url == null) throw new IllegalArgumentException("url is required");
            return new SimpleDataSource(this);
        }
    }
}