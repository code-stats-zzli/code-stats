package com.omni.business.datasource.service;

import com.omni.common.datasource.DynamicDataSourceManager;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.util.List;

@Service
public class DataSourceConfigService {
    Logger logger = LoggerFactory.getLogger(DataSourceConfigService.class);
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

    public List<DataSourceConfig> listAll() throws Exception {
        return jdbcTemplate.queryForList("SELECT * FROM datasource_config WHERE status = 1", DataSourceConfig.class);
    }

    public DataSourceConfig getById(String id) throws Exception {
        return jdbcTemplate.queryForObject("SELECT * FROM datasource_config WHERE id = ?", DataSourceConfig.class, id);
    }

    public boolean save(DataSourceConfig config) throws Exception {
        config.generateIdIfNull();
        if (config.getId() == null || config.getId().isEmpty()) {
            // 新增
            int rows = jdbcTemplate.update(
                    "INSERT INTO datasource_config (id, name, db_type, host, port, database_name, username, password, " +
                            "driver_class, driver_jar, connection_properties, version, status, driver_code, create_time, update_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    config.getId(), config.getName(), config.getDbType(), config.getHost(), config.getPort(),
                    config.getDatabaseName(), config.getUsername(), config.getPassword(), config.getDriverClass(),
                    config.getDriverJar(), config.getConnectionProperties(), config.getVersion(),
                    config.getStatus() != null ? config.getStatus() : 1, config.getDriverCode()
            );
            return rows > 0;
        } else {
            // 更新
            int rows = jdbcTemplate.update(
                    "UPDATE datasource_config SET name=?, db_type=?, host=?, port=?, database_name=?, username=?, password=?, " +
                            "driver_class=?, driver_jar=?, connection_properties=?, version=?, status=?, driver_code=?, update_time=NOW() WHERE id=?",
                    config.getName(), config.getDbType(), config.getHost(), config.getPort(), config.getDatabaseName(),
                    config.getUsername(), config.getPassword(), config.getDriverClass(), config.getDriverJar(),
                    config.getConnectionProperties(), config.getVersion(), config.getStatus(), config.getDriverCode(), config.getId()
            );
            if (rows > 0 && config.getStatus() == 1) {
                DynamicDataSourceManager.refreshDataSource(config);
            }
            return rows > 0;
        }
    }

    public boolean delete(String id) throws Exception {
        DynamicDataSourceManager.removeDataSource(id);
        return jdbcTemplate.update("DELETE FROM datasource_config WHERE id = ?", id) > 0;
    }

    public boolean testConnection(DataSourceConfig config) {
        try {
            boolean check = config.generateIdIfNull();
            DynamicDataSourceManager.getOrCreateDataSource(config).getConnection().close();
            if (check) DynamicDataSourceManager.removeDataSource(config.getId());
            return true;
        } catch (Exception e) {
            logger.error("testConnection error:", e);
            return false;
        }
    }

    public void loadAllOnStartup() throws Exception {
        List<DataSourceConfig> list = listAll();
        for (DataSourceConfig config : list) {
            DynamicDataSourceManager.getOrCreateDataSource(config);
        }
    }
}