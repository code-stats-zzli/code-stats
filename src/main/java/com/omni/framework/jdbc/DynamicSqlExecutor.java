package com.omni.framework.jdbc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicSqlExecutor {
    private static final Logger log = LoggerFactory.getLogger(DynamicSqlExecutor.class);
    private final JdbcTemplate jdbcTemplate;

    public DynamicSqlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> executeQuery(String sql, Map<String, Object> params) {
        String parsedSql = parseSql(sql, params);
        Object[] args = buildArgs(sql, params);
        try {
            return jdbcTemplate.queryForMapList(parsedSql, args);
        } catch (Exception e) {
            log.error("动态SQL执行失败: {}", parsedSql, e);
            throw new RuntimeException("动态SQL执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分页查询，自动适配不同数据库方言
     */
    public Map<String, Object> executePageQuery(String sql, Map<String, Object> params, int page, int pageSize)
            throws Exception {
        // 查询总数
        String countSql = "SELECT COUNT(*) FROM (" + parseSql(sql, params) + ") AS cnt";
        Object[] args = buildArgs(sql, params);
        long total = 0;
        try {
            total = jdbcTemplate.queryOne(countSql, rs -> rs.getLong(1), args);
        } catch (Exception e) {
            log.error("统计总数失败", e);
        }

        // 分页数据（方言处理）
        String pageSql = generatePageSql(parseSql(sql, params), page, pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForMapList(pageSql, args);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 根据数据库类型生成分页SQL
     */
    private String generatePageSql(String baseSql, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        DataSourceUtils.DbType dbType = DataSourceUtils.getDbType();

        switch (dbType) {
            case ORACLE:
                // 使用 ROWNUM 实现分页（兼容 Oracle 9i+）
                // 注意：需要确保 baseSql 本身没有 ORDER BY 被覆盖，这里简单包裹
                String oracleSql = "SELECT * FROM (SELECT t.*, ROWNUM rn FROM (" + baseSql + ") t WHERE ROWNUM <= " + (offset + pageSize) + ") WHERE rn > " + offset;
                return oracleSql;
            case MYSQL:
            default:
                // MySQL 使用 LIMIT
                return baseSql + " LIMIT " + offset + "," + pageSize;
        }
    }

    private String parseSql(String sql, Map<String, Object> params) {
        String result = sql;
        if (params.containsKey("tableName")) {
            String tableName = params.get("tableName").toString();
            result = result.replace("#{tableName}", tableName);
        }
        return result;
    }

    private Object[] buildArgs(String sql, Map<String, Object> params) {
        List<Object> args = new ArrayList<>();
        Pattern pattern = Pattern.compile("#\\{(\\w+)\\}");
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!"tableName".equals(key)) {
                args.add(params.get(key));
            }
        }
        return args.toArray();
    }
}