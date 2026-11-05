package com.omni.framework.jdbc;

import com.omni.framework.jdbc.dialect.SqlDialect;
import com.omni.framework.jdbc.dialect.SqlDialectRegistry;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DynamicSqlExecutor {
    private static final Logger log = LoggerFactory.getLogger(DynamicSqlExecutor.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    private SqlDialectRegistry dialectRegistry;

    public DynamicSqlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 兼容旧的 new DynamicSqlExecutor(jdbcTemplate) 用法（无 IoC 时回退到 MySQL） */
    public DynamicSqlExecutor() {
        this.jdbcTemplate = new JdbcTemplate();
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
     * 分页查询：方言通过 {@link SqlDialectRegistry} 动态获取，不再 switch 硬编码。
     */
    public Map<String, Object> executePageQuery(String sql, Map<String, Object> params,
                                                int page, int pageSize) throws Exception {
        String countSql = "SELECT COUNT(*) FROM (" + parseSql(sql, params) + ") AS cnt";
        Object[] args = buildArgs(sql, params);
        long total = 0;
        try {
            total = jdbcTemplate.queryOne(countSql, rs -> rs.getLong(1), args);
        } catch (Exception e) {
            log.error("统计总数失败", e);
        }

        String pageSql = generatePageSql(parseSql(sql, params), page, pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForMapList(pageSql, args);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    private String generatePageSql(String baseSql, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        DataSourceUtils.DbType dbType = DataSourceUtils.getDbType();
        String dialectName = dbType == DataSourceUtils.DbType.ORACLE ? "oracle" : "mysql";
        SqlDialect dialect = getDialect(dialectName);
        return dialect.buildPageSql(baseSql, offset, pageSize);
    }

    private SqlDialect getDialect(String name) {
        if (dialectRegistry != null) {
            SqlDialect d = dialectRegistry.get(name);
            if (d != null) return d;
        }
        // 降级：直接 new（兼容未走 IoC 的场景）
        return "oracle".equals(name)
                ? new com.omni.framework.jdbc.dialect.OracleDialect()
                : new com.omni.framework.jdbc.dialect.MysqlDialect();
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