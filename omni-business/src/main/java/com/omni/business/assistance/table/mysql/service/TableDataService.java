package com.omni.business.assistance.table.mysql.service;

import com.omni.framework.jdbc.DynamicSqlExecutor;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.spring.annotation.Service;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TableDataService {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicSqlExecutor sqlExecutor;

    public TableDataService() {
        this.jdbcTemplate = new JdbcTemplate();
        this.sqlExecutor = new DynamicSqlExecutor(jdbcTemplate);
    }

    /**
     * 分页查询表数据
     * @param database 数据库名
     * @param tableName 表名
     * @param page 页码（1开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public Map<String, Object> getTableData(String database, String tableName, int page, int pageSize) {
        // 切换数据库（确保查询正确的库）
        try (Connection conn = com.omni.framework.jdbc.DataSourceUtils.getConnection()) {
            conn.setCatalog(database);
        } catch (Exception e) {
            throw new RuntimeException("切换数据库失败: " + database, e);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("tableName", tableName);
        String sql = "SELECT * FROM #{tableName}";
        try {
            return sqlExecutor.executePageQuery(sql, params, page, pageSize);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行自定义SQL查询（用于SQL查询标签页）
     * @param database 数据库名
     * @param sql 用户输入的SQL（可以是SELECT、SHOW等）
     * @return 查询结果（列名+数据行）
     */
    public List<Map<String, Object>> executeCustomSql(String database, String sql) {
        try (Connection conn = com.omni.framework.jdbc.DataSourceUtils.getConnection()) {
            conn.setCatalog(database);
        } catch (Exception e) {
            throw new RuntimeException("切换数据库失败: " + database, e);
        }
        // 仅允许查询类语句，防止注入修改
        String trimmed = sql.trim().toLowerCase();
        if (!trimmed.startsWith("select") && !trimmed.startsWith("show") && !trimmed.startsWith("describe")) {
            throw new RuntimeException("仅支持SELECT/SHOW/DESCRIBE查询语句");
        }
        try {
            return jdbcTemplate.queryForMapList(sql);
        } catch (Exception e) {
            throw new RuntimeException("SQL执行失败: " + e.getMessage(), e);
        }
    }
}