package com.omni.framework.jdbc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态SQL执行器，支持表名参数替换和分页查询
 */
public class DynamicSqlExecutor {
    private static final Logger log = LoggerFactory.getLogger(DynamicSqlExecutor.class);
    private final JdbcTemplate jdbcTemplate;

    public DynamicSqlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行动态SQL（支持 #{tableName} 占位符，自动替换为实际表名）
     * @param sql SQL模板，例如 "SELECT * FROM #{tableName} WHERE id = #{id}"
     * @param params 参数（键值对），其中必须包含 tableName
     * @return 查询结果列表（Map形式）
     */
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
     * 分页查询（MySQL风格）
     * @param sql 原始SQL模板（不含LIMIT）
     * @param params 参数
     * @param page 页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页结果 {records, total, page, pageSize}
     */
    public Map<String, Object> executePageQuery(String sql, Map<String, Object> params, int page, int pageSize) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        // 查询总数
        String countSql = "SELECT COUNT(*) FROM (" + parseSql(sql, params) + ") AS cnt";
        Object[] args = buildArgs(sql, params);
        long total = 0;
        try {
            total = jdbcTemplate.queryOne(countSql, rs -> rs.getLong(1), args);
        } catch (Exception e) {
            log.error("统计总数失败", e);
        }

        // 分页数据
        String pageSql = parseSql(sql, params) + " LIMIT " + (page - 1) * pageSize + "," + pageSize;
        List<Map<String, Object>> records = jdbcTemplate.queryForMapList(pageSql, args);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    private String parseSql(String sql, Map<String, Object> params) {
        String result = sql;
        if (params.containsKey("tableName")) {
            String tableName = params.get("tableName").toString();
            result = result.replace("#{tableName}", tableName);
        }
        // 其他 #{key} 占位符由JdbcTemplate处理，故保留为?
        return result;
    }

    private Object[] buildArgs(String sql, Map<String, Object> params) {
        // 提取除了 tableName 之外的其他参数（按出现顺序）
        List<Object> args = new ArrayList<>();
        int idx = 0;
        while ((idx = sql.indexOf("#{", idx)) != -1) {
            int end = sql.indexOf("}", idx + 2);
            if (end == -1) break;
            String key = sql.substring(idx + 2, end);
            if (!"tableName".equals(key)) {
                args.add(params.get(key));
            }
            idx = end + 1;
        }
        return args.toArray();
    }
}