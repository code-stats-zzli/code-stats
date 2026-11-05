package com.omni.common.datasource.type.impl;

import com.omni.common.datasource.model.DataSourceConfig;

import java.util.Properties;

/**
 * Oracle 数据源类型。
 * JDBC URL 形如：jdbc:oracle:thin:@//host:port/serviceName
 */
public class OracleDataSourceType extends RDBMSDataSourceType {

    @Override
    public String getDriverClass(DataSourceConfig config) {
        if (config.getDriverClass() != null && !config.getDriverClass().trim().isEmpty()) {
            return config.getDriverClass();
        }
        return "oracle.jdbc.OracleDriver";
    }

    @Override
    public String getJdbcUrl(DataSourceConfig config) {
        return "jdbc:oracle:thin:@//" + config.getHost() + ":" + config.getPort()
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