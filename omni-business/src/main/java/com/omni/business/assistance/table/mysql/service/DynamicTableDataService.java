package com.omni.business.assistance.table.mysql.service;

import com.omni.framework.jdbc.DynamicSqlExecutor;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.pool.SimpleDataSource;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态表数据服务。
 * <p>★ L2-03：使用 {@code withCatalog} 修正 setCatalog 失效 bug。</p>
 */
public class DynamicTableDataService {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicSqlExecutor sqlExecutor;

    public DynamicTableDataService(SimpleDataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate() {
            @Override
            public Connection getConnection() throws java.sql.SQLException {
                return dataSource.getConnection();
            }
        };
        this.sqlExecutor = new DynamicSqlExecutor(this.jdbcTemplate);
    }

    public Map<String, Object> getTableData(String database, String tableName, int page, int pageSize) {
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", tableName);
        String sql = "SELECT * FROM #{tableName}";
        try {
            return jdbcTemplate.withCatalog(database, conn -> {
                try { return sqlExecutor.executePageQuery(sql, params, page, pageSize); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("切换数据库失败: " + database, e);
        }
    }

    public List<Map<String, Object>> executeCustomSql(String database, String sql) {
        String trimmed = sql.trim().toLowerCase();
        if (!trimmed.startsWith("select") && !trimmed.startsWith("show") && !trimmed.startsWith("describe")) {
            throw new RuntimeException("仅支持SELECT/SHOW/DESCRIBE查询语句");
        }
        try {
            return jdbcTemplate.withCatalog(database, conn -> {
                try { return jdbcTemplate.queryForMapList(sql); }
                catch (Exception e) { throw new RuntimeException("SQL执行失败: " + e.getMessage(), e); }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("切换数据库失败: " + database, e);
        }
    }
}