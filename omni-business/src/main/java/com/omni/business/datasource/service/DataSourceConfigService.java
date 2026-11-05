package com.omni.business.datasource.service;

import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DataSourceConfigService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfigService.class);

    private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

    // ==================== 查询 ====================

    public List<DataSourceConfig> listAll() throws Exception {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM datasource_config WHERE status = 1",
                    DataSourceConfig.class);
        } catch (Exception e) {
            log.error("查询数据源列表失败", e);
            throw e;
        }
    }

    public DataSourceConfig getById(String id) throws Exception {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源 id 不能为空");
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM datasource_config WHERE id = ?",
                    DataSourceConfig.class, id);
        } catch (Exception e) {
            log.error("按 id 查询数据源失败, id={}", id, e);
            throw e;
        }
    }

    // ==================== 保存（核心修复） ====================

    /**
     * 保存数据源：
     *   id 为空 → 新增（生成 UUID 后 INSERT）
     *   id 非空 → 更新（UPDATE ... WHERE id=?）
     *
     * ★ 修复点：原实现先 config.generateIdIfNull() 把 id 填上，再判断 id 是否为空，
     *   导致 if 分支永远进不去，新增时实际执行 UPDATE 影响 0 行、返回 false 且无异常，
     *   于是"保存失败但无日志"。
     */
    public boolean save(DataSourceConfig config) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("数据源配置不能为空");
        }

        // 关键：先记录"是否新增"，再决定是否生成 id
        boolean isNew = (config.getId() == null || config.getId().trim().isEmpty());

        // ==================== 新增 ====================
        if (isNew) {
            config.setId(UUID.randomUUID().toString().replace("-", ""));
            int status = config.getStatus() != null ? config.getStatus() : 1;

            log.info("新增数据源: id={}, name={}, dbType={}, host={}:{}/{}, user={}, status={}",
                    config.getId(), config.getName(), config.getDbType(),
                    config.getHost(), config.getPort(), config.getDatabaseName(),
                    config.getUsername(), status);

            int rows;
            try {
                rows = jdbcTemplate.update(
                        "INSERT INTO datasource_config (id, name, db_type, host, port, database_name, " +
                                "username, password, driver_class, driver_jar, connection_properties, " +
                                "version, status, create_time, update_time) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                        config.getId(), config.getName(), config.getDbType(),
                        config.getHost(), config.getPort(), config.getDatabaseName(),
                        config.getUsername(), config.getPassword(), config.getDriverClass(),
                        config.getDriverJar(), config.getConnectionProperties(),
                        config.getVersion(), status
                );
            } catch (Exception e) {
                log.error("新增数据源 SQL 执行失败: id={}, name={}, 原因={}",
                        config.getId(), config.getName(), rootCauseMessage(e), e);
                throw e;
            }

            if (rows <= 0) {
                log.warn("新增数据源影响行数为 0: id={}, name={}", config.getId(), config.getName());
                return false;
            }
            log.info("新增数据源成功: id={}, 影响行数={}", config.getId(), rows);

            // 新增后如果状态为启用，注册到动态数据源池
            if (status == 1) {
                try {
                    DynamicDataSourceManager.getOrCreateDataSource(config);
                    log.info("数据源已注册到动态池: id={}", config.getId());
                } catch (Exception e) {
                    log.warn("数据源已入库但注册到动态池失败: id={}", config.getId(), e);
                    // 入库已成功，注册失败不抛出
                }
            }
            return true;
        }

        // ==================== 更新 ====================
        log.info("更新数据源: id={}, name={}, dbType={}, host={}:{}/{}, status={}",
                config.getId(), config.getName(), config.getDbType(),
                config.getHost(), config.getPort(), config.getDatabaseName(),
                config.getStatus());

        int rows;
        try {
            rows = jdbcTemplate.update(
                    "UPDATE datasource_config SET name=?, db_type=?, host=?, port=?, " +
                            "database_name=?, username=?, password=?, driver_class=?, driver_jar=?, " +
                            "connection_properties=?, version=?, status=?, update_time=NOW() " +
                            "WHERE id=?",
                    config.getName(), config.getDbType(), config.getHost(), config.getPort(),
                    config.getDatabaseName(), config.getUsername(), config.getPassword(),
                    config.getDriverClass(), config.getDriverJar(), config.getConnectionProperties(),
                    config.getVersion(), config.getStatus(), config.getId()
            );
        } catch (Exception e) {
            log.error("更新数据源 SQL 执行失败: id={}, name={}, 原因={}",
                    config.getId(), config.getName(), rootCauseMessage(e), e);
            throw e;
        }

        if (rows <= 0) {
            log.warn("更新数据源影响行数为 0（记录可能不存在）: id={}, name={}",
                    config.getId(), config.getName());
            return false;
        }
        log.info("更新数据源成功: id={}, 影响行数={}", config.getId(), rows);

        // 状态为启用时刷新动态数据源；否则从池中移除
        if (config.getStatus() != null && config.getStatus() == 1) {
            try {
                DynamicDataSourceManager.refreshDataSource(config);
                log.info("数据源已刷新: id={}", config.getId());
            } catch (Exception e) {
                log.warn("数据源已更新但刷新动态池失败: id={}", config.getId(), e);
            }
        } else {
            try {
                DynamicDataSourceManager.removeDataSource(config.getId());
                log.info("数据源已从动态池移除（status!=1）: id={}", config.getId());
            } catch (Exception e) {
                log.warn("移除动态数据源失败: id={}", config.getId(), e);
            }
        }
        return true;
    }

    // ==================== 删除 ====================

    public boolean delete(String id) throws Exception {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源 id 不能为空");
        }
        log.info("删除数据源: id={}", id);
        try {
            DynamicDataSourceManager.removeDataSource(id);
        } catch (Exception e) {
            log.warn("移除动态数据源失败（可能未注册）: id={}", id, e);
        }
        try {
            int rows = jdbcTemplate.update("DELETE FROM datasource_config WHERE id = ?", id);
            log.info("删除数据源完成: id={}, 影响行数={}", id, rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("删除数据源 SQL 执行失败: id={}, 原因={}", id, rootCauseMessage(e), e);
            throw e;
        }
    }

    // ==================== 连接测试 ====================

    public boolean testConnection(DataSourceConfig config) {
        if (config == null) {
            log.warn("testConnection: 配置为空");
            return false;
        }
        log.info("测试数据源连接: dbType={}, host={}:{}/{}, user={}",
                config.getDbType(), config.getHost(), config.getPort(),
                config.getDatabaseName(), config.getUsername());

        boolean generatedId = false;
        try {
            // generateIdIfNull 返回 true 表示"本次生成了临时 id"，用完要清理
            generatedId = config.generateIdIfNull();
            DynamicDataSourceManager.getOrCreateDataSource(config).getConnection().close();
            log.info("测试数据源连接成功: dbType={}, host={}:{}",
                    config.getDbType(), config.getHost(), config.getPort());
            return true;
        } catch (Exception e) {
            log.error("测试数据源连接失败: dbType={}, host={}:{}/{}, 原因={}",
                    config.getDbType(), config.getHost(), config.getPort(),
                    config.getDatabaseName(), rootCauseMessage(e), e);
            return false;
        } finally {
            if (generatedId) {
                try {
                    DynamicDataSourceManager.removeDataSource(config.getId());
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ==================== 启动加载 ====================

    public void loadAllOnStartup() throws Exception {
        List<DataSourceConfig> list = listAll();
        log.info("启动时加载数据源，共 {} 个", list.size());
        for (DataSourceConfig config : list) {
            try {
                DynamicDataSourceManager.getOrCreateDataSource(config);
                log.info("数据源已加载: id={}, name={}", config.getId(), config.getName());
            } catch (Exception e) {
                log.error("数据源加载失败: id={}, name={}, 原因={}",
                        config.getId(), config.getName(), rootCauseMessage(e), e);
                // 单个失败不影响启动
            }
        }
    }

    // ==================== 工具 ====================

    private String rootCauseMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + ": " + (msg == null ? "(no message)" : msg);
    }
}