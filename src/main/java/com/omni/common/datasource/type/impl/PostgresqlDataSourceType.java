package com.omni.common.datasource.type.impl;

import com.omni.common.datasource.model.DataSourceConfig;

import java.util.Properties;

/**
 * PostgreSQL 数据源类型。
 * JDBC URL 形如：jdbc:postgresql://host:port/database
 */
public class PostgresqlDataSourceType extends RDBMSDataSourceType {

    @Override
    public String getDriverClass(DataSourceConfig config) {
        if (config.getDriverClass() != null && !config.getDriverClass().trim().isEmpty()) {
            return config.getDriverClass();
        }
        return "org.postgresql.Driver";
    }

    @Override
    public String getJdbcUrl(DataSourceConfig config) {
        return "jdbc:postgresql://" + config.getHost() + ":" + config.getPort()
                + "/" + config.getDatabaseName();
    }

    @Override
    public Properties getConnectionProperties(DataSourceConfig config) {
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        props.setProperty("password", config.getPassword());
        if (config.getConnectionProperties() != null) {
            for (String pair : config.getConnectionProperties().split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) props.setProperty(kv[0], kv[1]);
            }
        }
        return props;
    }
}