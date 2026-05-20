package com.omni.common.datasource.type;

import com.omni.common.datasource.model.DataSourceConfig;

import java.sql.SQLException;
import java.util.Properties;

public interface DataSourceType {
    String getDriverClass(DataSourceConfig config);
    String getJdbcUrl(DataSourceConfig config);
    Properties getConnectionProperties(DataSourceConfig config);
    boolean testConnection(DataSourceConfig config) throws SQLException;
}