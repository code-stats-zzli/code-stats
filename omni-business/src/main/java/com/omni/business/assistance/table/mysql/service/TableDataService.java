package com.omni.business.assistance.table.mysql.service;

import com.omni.framework.jdbc.DynamicSqlExecutor;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.spring.annotation.Service;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表数据服务。
 * <p>★ L2-03：使用 withCatalog。</p>
 */
@Service
public class TableDataService {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicSqlExecutor sqlExecutor;

    public TableDataService() {
        this.jdbcTemplate = new JdbcTemplate();
        this.sqlExecutor = new DynamicSqlExecutor(jdbcTemplate);
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