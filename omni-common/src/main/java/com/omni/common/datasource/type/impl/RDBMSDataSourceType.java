package com.omni.common.datasource.type.impl;

import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.common.datasource.type.DataSourceType;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class RDBMSDataSourceType implements DataSourceType {
    @Override
    public String getDriverClass(DataSourceConfig config) {
        if (config.getDriverClass() != null) return config.getDriverClass();
        // 默认根据版本选择驱动
        String version = config.getVersion();
        if (version != null && version.startsWith("5")) {
            return "com.mysql.jdbc.Driver";
        }
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public String getJdbcUrl(DataSourceConfig config) {
        return "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabaseName()
                + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    }

    @Override
    public Properties getConnectionProperties(DataSourceConfig config) {
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        props.setProperty("password", config.getPassword());
        // 解析额外的连接属性
        if (config.getConnectionProperties() != null) {
            for (String pair : config.getConnectionProperties().split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) props.setProperty(kv[0], kv[1]);
            }
        }
        return props;
    }

    @Override
    public boolean testConnection(DataSourceConfig config) throws SQLException {
        try {
            Class.forName(getDriverClass(config));
        } catch (ClassNotFoundException e) {
            throw new SQLException("驱动类未找到: " + getDriverClass(config), e);
        }
        Properties props = getConnectionProperties(config);
        try (Connection conn = DriverManager.getConnection(getJdbcUrl(config), props)) {
            return conn.isValid(3);
        }
    }
}