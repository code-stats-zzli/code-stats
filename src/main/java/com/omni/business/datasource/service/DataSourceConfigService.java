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

    // 查询所有启用的数据源
    public List<DataSourceConfig> listAll() throws Exception {
        return jdbcTemplate.queryForList("SELECT * FROM datasource_config WHERE status = 1", DataSourceConfig.class);
    }

    // 根据ID查询（ID 为 String）
    public DataSourceConfig getById(String id) throws Exception {
        return jdbcTemplate.queryForObject("SELECT * FROM datasource_config WHERE id = ?", DataSourceConfig.class, id);
    }

    // 保存（新增或更新）
    public boolean save(DataSourceConfig config) throws Exception {
        config.generateIdIfNull();   // 自动生成 UUID
        if (config.getId() == null || config.getId().isEmpty()) {
            // 新增
            int rows = jdbcTemplate.update(
                    "INSERT INTO datasource_config (id, name, db_type, host, port, database_name, username, password, driver_class, driver_jar, connection_properties, version, status, create_time, update_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    config.getId(), config.getName(), config.getDbType(), config.getHost(), config.getPort(),
                    config.getDatabaseName(), config.getUsername(), config.getPassword(), config.getDriverClass(),
                    config.getDriverJar(), config.getConnectionProperties(), config.getVersion(),
                    config.getStatus() != null ? config.getStatus() : 1);
            return rows > 0;
        } else {
            // 更新
            int rows = jdbcTemplate.update(
                    "UPDATE datasource_config SET name=?, db_type=?, host=?, port=?, database_name=?, username=?, password=?, " +
                            "driver_class=?, driver_jar=?, connection_properties=?, version=?, status=?, update_time=NOW() WHERE id=?",
                    config.getName(), config.getDbType(), config.getHost(), config.getPort(), config.getDatabaseName(),
                    config.getUsername(), config.getPassword(), config.getDriverClass(), config.getDriverJar(),
                    config.getConnectionProperties(), config.getVersion(), config.getStatus(), config.getId());
            if (rows > 0 && config.getStatus() == 1) {
                DynamicDataSourceManager.refreshDataSource(config);
            }
            return rows > 0;
        }
    }

    // 删除数据源
    public boolean delete(String id) throws Exception {
        DynamicDataSourceManager.removeDataSource(id);
        return jdbcTemplate.update("DELETE FROM datasource_config WHERE id = ?", id) > 0;
    }

    // 测试连接（使用配置对象，与ID无关）
    public boolean testConnection(DataSourceConfig config) {
        try {
            boolean check = config.generateIdIfNull();
            DynamicDataSourceManager.getOrCreateDataSource(config).getConnection().close();
            if(check)DynamicDataSourceManager.removeDataSource(config.getId());
            return true;
        } catch (Exception e) {
            logger.error("testConnection error:", e);
            return false;
        }
    }

    // 启动时加载所有启用的数据源
    public void loadAllOnStartup() throws Exception {
        List<DataSourceConfig> list = listAll();
        for (DataSourceConfig config : list) {
            DynamicDataSourceManager.getOrCreateDataSource(config);
        }
    }
}